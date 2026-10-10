package com.aether.play

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/* --- THE BACKGROUND AUDIO SERVICE --- */
// This keeps the app playing when you lock the phone or switch apps
class AetherAudioService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player!!).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

// Aether Play Theme Colors
val AetherDark = Color(0xFF020205)
val AetherCyan = Color(0xFF00F2FE)
val AetherMagenta = Color(0xFFFE0979)
val GlassWhite = Color(0x1AFFFFFF)

class MainActivity : ComponentActivity() {
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var audioController: MediaController? = null
    private var deviceEqualizer: Equalizer? = null

    // Permission launcher for scanning local audio
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Load local songs (simplified for single file)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Connect to the Background Service
        val sessionToken = SessionToken(this, ComponentName(this, AetherAudioService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        
        controllerFuture.addListener({
            audioController = controllerFuture.get()
            // Hook up Hardware Equalizer
            audioController?.let { controller ->
                // Note: Getting audio session ID requires ExoPlayer instance casting in a real app,
                // but this sets up the architecture for it.
            }
        }, ContextCompat.getMainExecutor(this))

        setContent {
            AetherAppUI(audioController)
        }

        // Request storage permissions on startup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MediaController.releaseFuture(controllerFuture)
        deviceEqualizer?.release()
    }
}

@Composable
fun AetherAppUI(controller: MediaController?) {
    var currentTab by remember { mutableStateOf("LIBRARY") }
    var isPlaying by remember { mutableStateOf(false) }
    
    // Listen to player state changes
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    Scaffold(
        bottomBar = { AetherBottomNav(currentTab) { currentTab = it } },
        containerColor = AetherDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0D1B2A), AetherDark)
                    )
                )
        ) {
            when (currentTab) {
                "LIBRARY" -> LibraryScreen(controller)
                "PLAYING" -> NowPlayingScreen(controller, isPlaying)
                "EQ" -> EqualizerScreen()
            }
        }
    }
}

@Composable
fun LibraryScreen(controller: MediaController?) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Logo", tint = AetherCyan, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Aether Play", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        
        // Mock Library Item
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassWhite)
                .clickable {
                    // Start playing a mock URL when clicked
                    val item = MediaItem.fromUri("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
                    controller?.setMediaItem(item)
                    controller?.prepare()
                    controller?.play()
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Cyberpunk Synth (Demo)", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Aether Audio", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun NowPlayingScreen(controller: MediaController?, isPlaying: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Album Art Glass Container
        Box(
            modifier = Modifier
                .size(300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(AetherMagenta.copy(alpha = 0.5f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.align(Alignment.Center).size(100.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        Text("Cyberpunk Synth", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Aether Audio", color = AetherCyan, fontSize = 16.sp)
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Playback Controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller?.seekToPreviousMediaItem() }) {
                Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(40.dp))
            }
            
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AetherCyan, AetherMagenta)))
                    .clickable { 
                        if (isPlaying) controller?.pause() else controller?.play() 
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            IconButton(onClick = { controller?.seekToNextMediaItem() }) {
                Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun EqualizerScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("7-Band Parametric EQ", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        // Device Tabs
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GlassWhite)) {
            Text("Speaker", color = AetherCyan, modifier = Modifier.weight(1f).padding(12.dp), fontWeight = FontWeight.Bold)
            Text("Bluetooth", color = Color.Gray, modifier = Modifier.weight(1f).padding(12.dp))
            Text("Wired", color = Color.Gray, modifier = Modifier.weight(1f).padding(12.dp))
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Text("Sliders will map to android.media.audiofx.Equalizer hardware limits here.", color = Color.Gray)
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun AetherBottomNav(currentTab: String, onTabSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        NavIcon(Icons.Default.LibraryMusic, "LIBRARY", currentTab, onTabSelected)
        NavIcon(Icons.Default.PlayCircle, "PLAYING", currentTab, onTabSelected)
        NavIcon(Icons.Default.Tune, "EQ", currentTab, onTabSelected)
    }
}
