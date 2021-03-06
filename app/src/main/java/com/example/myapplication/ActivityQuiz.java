package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.media.session.MediaButtonReceiver;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;

public class ActivityQuiz extends AppCompatActivity implements View.OnClickListener, ExoPlayer.EventListener {
    private static final String TAG = "QuizActivity";
    //views
    SimpleExoPlayer mExoPlayer;
    boolean flag = false;
    PlayerView mPlayerView;
    Button[] mButtons;
    int[] mButtonsIDs = {R.id.buttonA, R.id.buttonB, R.id.buttonC, R.id.buttonD};
    //delay
    private static final int CORRECT_ANSWER_DELAY = 1000;

    ImageView fullscreenButton;

    //vars
    private ArrayList<Integer> mQuestionsSongIds;
    private ArrayList<Integer> mRemainingSongIds;
    private int mCorrectSongId;
    private int mCurrentScore;
    private int mHighScore;

    //intents
    private static final String REMAINING_SONGS_KEY = "remaining_songs";

    //session
    private static MediaSessionCompat mMediaSession;
    private PlaybackStateCompat.Builder mStateBuilder;
    NotificationChannel channel;
    NotificationManager notificationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);
        fullscreenButton = findViewById(R.id.exo_fullscreen_icon);
        mPlayerView = findViewById(R.id.playerView);
        createNotificationChannel();
        //check whether its the new game
        boolean isNewGame = !getIntent().hasExtra(REMAINING_SONGS_KEY);

        if (isNewGame) {
            //score reset
            QuizUtils.setCurrentScore(this, 0);

            //get all songs
            mRemainingSongIds = Song.getAllSongIds(this);
        } else {
            //get the remaining songs
            mRemainingSongIds = getIntent().getIntegerArrayListExtra(REMAINING_SONGS_KEY);
        }

        // get the current and high score
        mCurrentScore = QuizUtils.getCurrentScore(this);
        mHighScore = QuizUtils.getHighScore(this);

        //generate question and also correct answer
        mQuestionsSongIds = QuizUtils.generateQuestion(mRemainingSongIds);
        //get the correct
        mCorrectSongId = QuizUtils.getCorrectAnswer(mQuestionsSongIds);

        //set album art
        mPlayerView.setDefaultArtwork(ResourcesCompat.getDrawable(
                getResources(),
                Song.getAlbumArtBySongId(this, mCorrectSongId),
                this.getTheme()));

        //end the game
        if (mQuestionsSongIds.size() < 2) {
            QuizUtils.endGame(this);
            finish();
        }

        //add button with their question title
        mButtons = initializeButtons(mQuestionsSongIds);

        //media session
        initializeMediaSession();

        //initialize songs and playerview
        Song answerSong = Song.getSongById(this, mCorrectSongId);
        if (answerSong == null) {
            Toast.makeText(this, "Song not found! Error", Toast.LENGTH_SHORT).show();
            return;
        }
        initializePlayer(Uri.parse(answerSong.getUri()));


        fullscreenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (flag) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) mPlayerView.getLayoutParams();
                    params.width=params.MATCH_PARENT;
                    params.height=950;
                    mPlayerView.setLayoutParams(params);
                    enableButtons(mQuestionsSongIds);
                    flag = false;
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) mPlayerView.getLayoutParams();
                    params.width=params.MATCH_PARENT;
                    params.height=params.MATCH_PARENT;
                    mPlayerView.setLayoutParams(params);
                    disableButtons(mQuestionsSongIds);
                    flag = true;
                }
            }
        });

    }


    //=========================On Focused Changed=====================================

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }


    // ===================hide system ui ============================
    private void hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    //=====================Show System Ui =============================
    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }


    // ================== PLAYBACK SESSION ==============================

    /**
     * Initialize Media Session
     */
    private void initializeMediaSession() {
        //create
        mMediaSession = new MediaSessionCompat(this, TAG);

        //enable callbacks for media buttons and transport control
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        //do not restart media session if app is not visible
        mMediaSession.setMediaButtonReceiver(null);

        //playback state
        mStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE
                );
        mMediaSession.setPlaybackState(mStateBuilder.build());

        // callbacks
        mMediaSession.setCallback(new MySessionCallback());

        //active or start the session
        mMediaSession.setActive(true);
    }

    // ====================== MEDIA NOTIFICATION =========================

    /**
     * Create notification channel
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = new NotificationChannel("media_notify", "media_notify",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Show and build notification channel
     */
    private void showNotification(PlaybackStateCompat state) {


        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "media_notify");

        int icon;
        String play_pause;
        if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
            icon = R.drawable.exo_controls_pause;
            play_pause = "Pause";
        } else {
            icon = R.drawable.exo_controls_play;
            play_pause = "Play";
        }

        NotificationCompat.Action playPauseAction = new NotificationCompat.Action(
                icon, play_pause,
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE));

        NotificationCompat.Action restartAction = new NotificationCompat.Action(
                R.drawable.exo_controls_previous, "Restart",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));


        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0,
                new Intent(this, ActivityQuiz.class),
                0);

        builder.setContentTitle("Guess the Artist")
                .setContentText("Press play to hear the song")
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_music)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(restartAction)
                .addAction(playPauseAction)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mMediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager = getSystemService(NotificationManager.class);
        } else {
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }
        notificationManager.notify(0, builder.build());

    }

    // ====================== BROADCAST RECEIVER =========================
    public static class MediaReceiver extends BroadcastReceiver {
        public MediaReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            MediaButtonReceiver.handleIntent(mMediaSession, intent);
        }
    }

    // ====================== CALLBACK CLASS =========================

    private class MySessionCallback extends MediaSessionCompat.Callback {
        @Override
        public void onPlay() {
            mExoPlayer.setPlayWhenReady(true);
        }

        @Override
        public void onPause() {
            mExoPlayer.setPlayWhenReady(false);
        }

        @Override
        public void onSkipToPrevious() {
            mExoPlayer.seekTo(0);
        }
    }


    // ====================== EVENT FUNCTIONS =========================

    @Override
    public void onTimelineChanged(Timeline timeline, int reason) {
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == ExoPlayer.STATE_READY && playWhenReady) {
            mStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                    mExoPlayer.getContentPosition(), 1f);
        } else if (playbackState == ExoPlayer.STATE_READY) {
            mStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                    mExoPlayer.getContentPosition(), 1f);
        }
        mMediaSession.setPlaybackState(mStateBuilder.build());
        showNotification(mStateBuilder.build());
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {

    }

    @Override
    public void onPositionDiscontinuity(int reason) {

    }

    // ====================== PLAYER FUNCTIONS =========================

    /**
     * Initialize Exoplayer to play songs
     */
    private void initializePlayer(Uri mediaUri) {
        if (mExoPlayer == null) {
            //build exoplayer
            mExoPlayer = new SimpleExoPlayer.Builder(this).build();
            mPlayerView.setPlayer(mExoPlayer);
            mExoPlayer.addListener(this);

            //media source
            String userAgent = Util.getUserAgent(this, "GuessArtist");
            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                    userAgent);
            MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaUri);
            mExoPlayer.prepare(mediaSource);
            mExoPlayer.setPlayWhenReady(true);
        }
    }

    /**
     * Release Exoplayer
     */
    private void releasePlayer() {
        notificationManager.cancelAll();
        mExoPlayer.stop();
        mExoPlayer.release();
        mExoPlayer = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        mMediaSession.setActive(false);
    }

    // ====================== VIEW FUNCTIONS =========================

    /**
     * First add the buttons to their respective ids
     * Second, song ko id add
     */
    private Button[] initializeButtons(ArrayList<Integer> answerSongIds) {
        Button[] buttons = new Button[mButtonsIDs.length];
        for (int i = 0; i < answerSongIds.size(); i++) {
            Button currentButton = findViewById(mButtonsIDs[i]);
            Song currentSong = Song.getSongById(this, answerSongIds.get(i));
            buttons[i] = currentButton;
            currentButton.setOnClickListener(this);
            if (currentSong != null) {
                currentButton.setText(currentSong.getArtist());
            }
        }
        return buttons;
    }



    /**
     * When option button is pressed
     */
    @Override
    public void onClick(View view) {
        //show correct answer
        showCorrectAnswer();

        //check the user answer
        Button pressedButton = (Button) view;

        //get the index of the button
        int userAnswerIndex = -1;
        for (int i = 0; i < mButtons.length; i++) {
            if (pressedButton.getId() == mButtonsIDs[i]) {
                userAnswerIndex = i;
            }
        }

        //check user correct
        if (QuizUtils.userCorrect(mCorrectSongId, userAnswerIndex)) {
            mCurrentScore++;
            QuizUtils.setCurrentScore(this, mCurrentScore);
            if (mCurrentScore > mHighScore) {
                mHighScore = mCurrentScore;
                QuizUtils.setHighScore(this, mHighScore);
            }
        }

        //remove the answer song from list of all songs
        mRemainingSongIds.remove(Integer.valueOf(mCorrectSongId));

        //wait for 2-3 seconds so that user can see the correct answer
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mExoPlayer.stop();
                                    Intent nextQuestionIntent = new Intent(ActivityQuiz.this, ActivityQuiz.class);
                                    nextQuestionIntent.putExtra(REMAINING_SONGS_KEY, mRemainingSongIds);
                                    finish();
                                    startActivity(nextQuestionIntent);
                                }
                            },
                CORRECT_ANSWER_DELAY);

    }

    /**
     * Display the correct answer
     */
    private void showCorrectAnswer() {
        mPlayerView.setDefaultArtwork(ResourcesCompat.getDrawable(
                getResources(),
                Song.getAlbumArtBySongId(this, mCorrectSongId),
                this.getTheme()));
        for (int i = 0; i < mQuestionsSongIds.size(); i++) {
            int buttonSongId = mQuestionsSongIds.get(i);
            mButtons[i].setEnabled(false);

            //check answer
            if (buttonSongId == mCorrectSongId) {
                mButtons[i].getBackground().setColorFilter(
                        ContextCompat.getColor(this, android.R.color.holo_green_light),
                        PorterDuff.Mode.MULTIPLY);
            } else {
                mButtons[i].getBackground().setColorFilter(
                        ContextCompat.getColor(this, android.R.color.holo_red_light),
                        PorterDuff.Mode.MULTIPLY);
            }
            mButtons[i].setTextColor(Color.WHITE);
        }
    }




    private Button[] disableButtons(ArrayList<Integer> answerSongIds) {
        Button[] buttons = new Button[mButtonsIDs.length];
        for (int i = 0; i < answerSongIds.size(); i++) {
            Button currentButton = findViewById(mButtonsIDs[i]);
            currentButton.setVisibility(View.INVISIBLE);
        }
        return buttons;
    }

    private Button[] enableButtons(ArrayList<Integer> answerSongIds) {
        Button[] buttons = new Button[mButtonsIDs.length];
        for (int i = 0; i < answerSongIds.size(); i++) {
            Button currentButton = findViewById(mButtonsIDs[i]);
            currentButton.setVisibility(View.VISIBLE);
        }
        return buttons;
    }



}