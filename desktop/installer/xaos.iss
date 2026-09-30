; L'installer di Xaos desktop, con Inno Setup 6.7.
;
; Lo costruisce il task Gradle `packageInstaller`, che passa versione e
; cartelle con /D. Aspetto coerente con l'app: stile Windows 11 che segue il
; tema di sistema, immagini a matrice di punti (make_images.py) chiare o scure,
; stesso colore di fondo dell'app.

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef SourceDir
  #error SourceDir non definito: va passato da Gradle (/DSourceDir=...)
#endif
#ifndef OutputDir
  #define OutputDir "."
#endif

[Setup]
; Fisso per sempre: è ciò che fa riconoscere un aggiornamento.
AppId={{8B3F2C71-5E0A-4D8B-9A64-2F1C7E9D4A30}
AppName=Xaos
AppVersion={#AppVersion}
AppVerName=Xaos {#AppVersion}
AppPublisher=XaosWasHere
AppPublisherURL=https://github.com/XaosWasHere/xaos-music-player
AppSupportURL=https://github.com/XaosWasHere/xaos-music-player/issues
AppUpdatesURL=https://github.com/XaosWasHere/xaos-music-player/releases
VersionInfoVersion={#AppVersion}
VersionInfoDescription=Installazione di Xaos
DefaultDirName={autopf}\Xaos
DefaultGroupName=Xaos
DisableProgramGroupPage=yes
DisableWelcomePage=no
DisableReadyPage=no
OutputDir={#OutputDir}
OutputBaseFilename=Xaos-{#AppVersion}
SetupIconFile=..\src\main\resources\xaos.ico
UninstallDisplayIcon={app}\Xaos.exe
UninstallDisplayName=Xaos
Compression=lzma2/ultra64
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
#ifdef PreviewOnly
PrivilegesRequired=lowest
#else
PrivilegesRequired=admin
#endif

; Aspetto: moderno, chiaro o scuro come Windows.
WizardStyle=modern dynamic windows11
WizardBackColor=#f2f2f2
WizardBackColorDynamicDark=#000000
WizardImageFile=wizard-light-164.bmp,wizard-light-246.bmp,wizard-light-328.bmp
WizardImageFileDynamicDark=wizard-dark-164.bmp,wizard-dark-246.bmp,wizard-dark-328.bmp
WizardSmallImageFile=small-light-55.bmp,small-light-83.bmp,small-light-110.bmp
WizardSmallImageFileDynamicDark=small-dark-55.bmp,small-dark-83.bmp,small-dark-110.bmp

; Xaos deve essere chiuso: lo stesso mutex con cui l'app impedisce una
; seconda istanza dice all'installer che è aperta.
AppMutex=XaosMusicPlayer.SingleInstance
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "it"; MessagesFile: "compiler:Languages\Italian.isl"

[Messages]
it.WelcomeLabel1=Benvenuto in Xaos
it.WelcomeLabel2=Il player musicale che tiene insieme PC e telefono.%n%nVerrà installato [name/ver]: la tua libreria, i preferiti e le playlist restano come sono.
it.FinishedHeadingLabel=Xaos è pronto
it.FinishedLabel=L'installazione è finita. Xaos si trova nel menu Start.
it.SetupAppRunningError=Xaos è aperto. Chiudilo e premi OK per continuare, o Annulla per uscire.

[Tasks]
Name: "desktopicon"; Description: "Crea un collegamento sul desktop"; GroupDescription: "Collegamenti:"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
; Un aggiornamento parte pulito: niente file della versione precedente che
; non esistono più (vecchi plugin, vecchi jar).
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Icons]
Name: "{autoprograms}\Xaos"; Filename: "{app}\Xaos.exe"
Name: "{autodesktop}\Xaos"; Filename: "{app}\Xaos.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Xaos.exe"; Description: "Avvia Xaos"; Flags: nowait postinstall skipifsilent

[Code]
{ Le versioni fino alla 1.3.1 si installavano con un pacchetto MSI (jpackage).
  Prima di installare questa si rimuove quella, trovandola dal suo UpgradeCode:
  altrimenti Windows mostrerebbe due Xaos fra i programmi installati. I dati
  dell'utente (~/.xaos) non stanno nella cartella del programma e restano. }

const
  OldUpgradeCode = '{64F0ED55-26A1-43A7-8814-F7F5064C6442}';

{ L'installer si porta davanti appena si apre: dopo la richiesta dei permessi
  di amministratore Windows lo lascerebbe spesso dietro le altre finestre.
  "Sempre in primo piano" per un istante, poi di nuovo normale, funziona anche
  quando SetForegroundWindow da solo viene ignorato. }
const
  HWND_TOPMOST = -1;
  HWND_NOTOPMOST = -2;
  SWP_NOMOVE = $0002;
  SWP_NOSIZE = $0001;
  SWP_SHOWWINDOW = $0040;

function SetWindowPos(hWnd: HWND; hWndInsertAfter: HWND; X, Y, cx, cy: Integer; uFlags: UINT): BOOL;
  external 'SetWindowPos@user32.dll stdcall';
function SetForegroundWindow(hWnd: HWND): BOOL;
  external 'SetForegroundWindow@user32.dll stdcall';

var
  BroughtToFront: Boolean;

procedure BringWizardToFront;
begin
  SetWindowPos(WizardForm.Handle, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_SHOWWINDOW);
  SetWindowPos(WizardForm.Handle, HWND_NOTOPMOST, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_SHOWWINDOW);
  SetForegroundWindow(WizardForm.Handle);
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if not BroughtToFront then
  begin
    BroughtToFront := True;
    BringWizardToFront;
  end;
end;

function MsiEnumRelatedProducts(lpUpgradeCode: String; dwReserved: Cardinal; iProductIndex: Cardinal; lpProductBuf: String): Cardinal;
  external 'MsiEnumRelatedProductsW@msi.dll stdcall';

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Buffer: String;
  ResultCode: Integer;
  Attempts: Integer;
begin
  Result := '';
  Attempts := 0;
  Buffer := StringOfChar(' ', 39);
  while (Attempts < 5) and (MsiEnumRelatedProducts(OldUpgradeCode, 0, 0, Buffer) = 0) do
  begin
    Attempts := Attempts + 1;
    WizardForm.PreparingLabel.Caption := 'Rimozione della versione precedente di Xaos…';
    if not Exec(ExpandConstant('{sys}\msiexec.exe'), '/x ' + Copy(Buffer, 1, 38) + ' /qn /norestart', '', SW_HIDE, ewWaitUntilTerminated, ResultCode)
      or ((ResultCode <> 0) and (ResultCode <> 3010)) then
    begin
      Result := 'Non è stato possibile rimuovere la versione precedente di Xaos (codice ' + IntToStr(ResultCode) + '). Disinstallala da Impostazioni > App e riprova.';
      Exit;
    end;
    Buffer := StringOfChar(' ', 39);
  end;
end;
