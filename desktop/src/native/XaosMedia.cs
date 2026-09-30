// Il ponte fra Xaos e i controlli multimediali di Windows (SMTC): il riquadro
// del volume e delle Impostazioni rapide, la schermata di blocco e i tasti
// multimediali della tastiera.
//
// Java non raggiunge le API WinRT, quindi questo programmino fa da tramite.
// Legge da stdin righe separate da tabulazioni:
//   meta <titolo> <artista> <album> <copertina> <durata ms>
//   state playing|paused|stopped <posizione ms>
//   clear
// e scrive su stdout i comandi ricevuti: play, pause, toggle, next, previous,
// stop, seek <ms>.
//
// Si compila con il csc di .NET Framework 4.8, che c'è su ogni Windows 10 e
// 11: niente runtime da installare. Per questo è scritto in C# 5.

using System;
using System.IO;
using System.Reflection;
using System.Text;
using System.Threading;
using Windows.Foundation;
using Windows.Media;
using Windows.Media.Playback;
using Windows.Storage;
using Windows.Storage.Streams;

[assembly: AssemblyTitle("Xaos")]
[assembly: AssemblyProduct("Xaos")]
[assembly: AssemblyDescription("Xaos")]
[assembly: AssemblyCompany("Xaos")]

static class XaosMedia
{
    static SystemMediaTransportControls smtc;
    static readonly object writeLock = new object();
    static TimeSpan duration = TimeSpan.Zero;
    static StreamWriter output;

    static void Emit(string line)
    {
        lock (writeLock)
        {
            output.WriteLine(line);
            output.Flush();
        }
    }

    [MTAThread]
    static int Main()
    {
        // Senza console (è un'app a finestre) le codifiche di Console non si
        // possono cambiare: si aprono i flussi a mano, in UTF-8.
        StreamReader input = new StreamReader(Console.OpenStandardInput(), new UTF8Encoding(false));
        output = new StreamWriter(Console.OpenStandardOutput(), new UTF8Encoding(false));

        // Il MediaPlayer non suona niente: serve solo perché porta con sé dei
        // controlli di sistema utilizzabili anche da un'app senza finestra.
        MediaPlayer player = new MediaPlayer();
        player.CommandManager.IsEnabled = false;
        smtc = player.SystemMediaTransportControls;
        smtc.IsEnabled = true;
        smtc.IsPlayEnabled = true;
        smtc.IsPauseEnabled = true;
        smtc.IsNextEnabled = true;
        smtc.IsPreviousEnabled = true;
        smtc.IsStopEnabled = true;
        smtc.PlaybackStatus = MediaPlaybackStatus.Closed;
        smtc.ButtonPressed += OnButton;
        smtc.PlaybackPositionChangeRequested += OnSeek;

        Emit("ready");
        string line;
        while ((line = input.ReadLine()) != null)
        {
            try { Handle(line); }
            catch (Exception e) { Emit("error " + e.Message.Replace('\n', ' ')); }
        }
        return 0;
    }

    static void OnButton(SystemMediaTransportControls sender, SystemMediaTransportControlsButtonPressedEventArgs args)
    {
        switch (args.Button)
        {
            case SystemMediaTransportControlsButton.Play: Emit("play"); break;
            case SystemMediaTransportControlsButton.Pause: Emit("pause"); break;
            case SystemMediaTransportControlsButton.Next: Emit("next"); break;
            case SystemMediaTransportControlsButton.Previous: Emit("previous"); break;
            case SystemMediaTransportControlsButton.Stop: Emit("stop"); break;
        }
    }

    static void OnSeek(SystemMediaTransportControls sender, PlaybackPositionChangeRequestedEventArgs args)
    {
        Emit("seek " + (long)args.RequestedPlaybackPosition.TotalMilliseconds);
    }

    static void Handle(string line)
    {
        string[] f = line.Split('\t');
        switch (f[0])
        {
            case "meta":
            {
                SystemMediaTransportControlsDisplayUpdater u = smtc.DisplayUpdater;
                u.ClearAll();
                u.Type = MediaPlaybackType.Music;
                u.MusicProperties.Title = Field(f, 1);
                u.MusicProperties.Artist = Field(f, 2);
                u.MusicProperties.AlbumTitle = Field(f, 3);
                string art = Field(f, 4);
                if (art.Length > 0 && File.Exists(art))
                {
                    StorageFile file = Wait(StorageFile.GetFileFromPathAsync(art));
                    if (file != null) u.Thumbnail = RandomAccessStreamReference.CreateFromFile(file);
                }
                u.Update();
                long ms;
                duration = long.TryParse(Field(f, 5), out ms) ? TimeSpan.FromMilliseconds(ms) : TimeSpan.Zero;
                Timeline(TimeSpan.Zero);
                break;
            }
            case "state":
            {
                string s = Field(f, 1);
                smtc.PlaybackStatus = s == "playing" ? MediaPlaybackStatus.Playing
                    : s == "paused" ? MediaPlaybackStatus.Paused
                    : MediaPlaybackStatus.Stopped;
                long ms;
                if (long.TryParse(Field(f, 2), out ms)) Timeline(TimeSpan.FromMilliseconds(ms));
                break;
            }
            case "clear":
                smtc.DisplayUpdater.ClearAll();
                smtc.DisplayUpdater.Update();
                smtc.PlaybackStatus = MediaPlaybackStatus.Closed;
                break;
        }
    }

    static void Timeline(TimeSpan position)
    {
        if (duration <= TimeSpan.Zero) return;
        SystemMediaTransportControlsTimelineProperties t = new SystemMediaTransportControlsTimelineProperties();
        t.StartTime = TimeSpan.Zero;
        t.EndTime = duration;
        t.MinSeekTime = TimeSpan.Zero;
        t.MaxSeekTime = duration;
        t.Position = position;
        smtc.UpdateTimelineProperties(t);
    }

    /** Attende un'operazione WinRT senza le estensioni async, che qui non ci sono. */
    static T Wait<T>(IAsyncOperation<T> op)
    {
        for (int i = 0; i < 400 && op.Status == AsyncStatus.Started; i++) Thread.Sleep(5);
        return op.Status == AsyncStatus.Completed ? op.GetResults() : default(T);
    }

    static string Field(string[] f, int i)
    {
        return i < f.Length ? f[i] : "";
    }
}
