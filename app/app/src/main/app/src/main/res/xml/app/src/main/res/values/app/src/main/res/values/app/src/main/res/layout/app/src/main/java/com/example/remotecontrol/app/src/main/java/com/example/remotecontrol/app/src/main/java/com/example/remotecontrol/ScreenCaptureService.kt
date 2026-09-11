package com.example.remotecontrol

import android.app.*
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.webrtc.*

class ScreenCaptureService : Service(), SignalingClient.Listener {

    private lateinit var signaling: SignalingClient
    private lateinit var eglBase: EglBase
    private lateinit var factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var mediaProjection: MediaProjection? = null
    private var videoCapturer: VideoCapturer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification())

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        val roomCode = intent.getStringExtra("roomCode") ?: return START_NOT_STICKY
        val serverUrl = intent.getStringExtra("serverUrl") ?: return START_NOT_STICKY

        setupWebRTC()

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        startCapture(mediaProjection!!)

        signaling = SignalingClient(serverUrl, roomCode, this)
        signaling.connect()

        return START_STICKY
    }

    private fun setupWebRTC() {
        eglBase = EglBase.create()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    private fun startCapture(projection: MediaProjection) {
        videoCapturer = ScreenCapturerAndroid(projection, object : MediaProjection.Callback() {})
        val surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val videoSource = factory.createVideoSource(true)
        videoCapturer?.initialize(surfaceHelper, applicationContext, videoSource.capturerObserver)

        val metrics = resources.displayMetrics
        videoCapturer?.startCapture(metrics.widthPixels, metrics.heightPixels, 15)

        val videoTrack = factory.createVideoTrack("screen0", videoSource)

        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        )

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                signaling.sendIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            }
            override fun onDataChannel(dc: DataChannel) {
                dc.regist
