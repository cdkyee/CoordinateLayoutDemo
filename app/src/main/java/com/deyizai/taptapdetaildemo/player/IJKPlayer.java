package com.deyizai.taptapdetaildemo.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.MediaController;


import java.io.IOException;
import java.util.Map;

import iapp.eric.utils.base.Trace;
import tv.danmaku.ijk.media.player.AndroidMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;



public class IJKPlayer implements MediaController.MediaPlayerControl {


    private String TAG = "#IjkVideoView";
    private Uri mUri;
    private Map<String, String> mHeaders;

    //render view type
    public static final int RENDER_NONE = 0;
    public static final int RENDER_SURFACE_VIEW = 1;
    public static final int RENDER_TEXTURE_VIEW = 2;

    // all possible internal states
    public static final int STATE_ERROR = -1;
    public static final int STATE_IDLE = 0;
    public static final int STATE_PREPARING = 1;
    public static final int STATE_PREPARED = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_PAUSED = 4;
    public static final int STATE_PLAYBACK_COMPLETED = 5;

    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;

    private IRenderView.ISurfaceHolder mSurfaceHolder = null;
    private IMediaPlayer mMediaPlayer = null;

    private int mVideoWidth;
    private int mVideoHeight;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mVideoRotationDegree;
    private IMediaPlayer.OnCompletionListener mOnCompletionListener;
    private IMediaPlayer.OnPreparedListener mOnPreparedListener;
    private int mCurrentBufferPercentage;
    private IMediaPlayer.OnErrorListener mOnErrorListener;
    private IMediaPlayer.OnInfoListener mOnInfoListener;
    private int mSeekWhenPrepared;  // recording the seek position while preparing
    private boolean mCanPause = true;
    private boolean mCanSeekBack = true;
    private boolean mCanSeekForward = true;

    private Context mAppContext;
    //private Settings mSettings;
    private IRenderView mRenderView;
    private int mVideoSarNum;
    private int mVideoSarDen;

    private long mPrepareStartTime = 0;
    private long mPrepareEndTime = 0;

    private long mSeekStartTime = 0;
    private long mSeekEndTime = 0;
    private boolean isFirst = true;




    public IJKPlayer(Context context) {
        mAppContext = context;
        //mSettings = new Settings(mAppContext);
    }

    private ViewGroup mRoot;
    public IJKPlayer(Context context, ViewGroup root) {
        mAppContext = context;
        mRoot = root;
        //mSettings = new Settings(mAppContext);
    }

    //-------------------------
    // Extend: Render
    //-------------------------

    //默认为false，采用surfaceview
    private boolean isUseTextureView = false;

    public void initRenders(int render) {

        switch (render) {
            case RENDER_NONE:
                setRenderView(null);
                break;
            case RENDER_SURFACE_VIEW:
                SurfaceRenderView renderView = new SurfaceRenderView(mAppContext);
                setRenderView(renderView);
                break;
            case RENDER_TEXTURE_VIEW:
                TextureRenderView renderView2 = new TextureRenderView(mAppContext);
                if (mMediaPlayer != null) {
                    renderView2.getSurfaceHolder().bindToMediaPlayer(mMediaPlayer);
                    renderView2.setVideoSize(mMediaPlayer.getVideoWidth(), mMediaPlayer.getVideoHeight());
                    renderView2.setVideoSampleAspectRatio(mMediaPlayer.getVideoSarNum(), mMediaPlayer.getVideoSarDen());
                    renderView2.setAspectRatio(mCurrentAspectRatio);
                }
                setRenderView(renderView2);
                break;
            default:
                break;
        }

    }

    public void setRenderView(IRenderView renderView) {
        if (mRenderView != null) {
            //TODO
            if (mMediaPlayer != null)
                mMediaPlayer.setDisplay(null);

            View renderUIView = mRenderView.getView();
            mRenderView.removeRenderCallback(mSHCallback);
            mRenderView = null;
            if (null!=mRoot){
                mRoot.removeView(renderUIView);
            }

        }
        if (renderView == null) {
            Trace.Info("renderView == null");
            return;
        }
        Trace.Info(TAG + " set new Render");

        mRenderView = renderView;
        renderView.setAspectRatio(mCurrentAspectRatio);
        if (mVideoWidth > 0 && mVideoHeight > 0)
            renderView.setVideoSize(mVideoWidth, mVideoHeight);
        if (mVideoSarNum > 0 && mVideoSarDen > 0)
            renderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);


        if (null!=mRoot){
            View renderUIView = mRenderView.getView();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER);
            renderUIView.setLayoutParams(lp);
            mRoot.addView(renderUIView,0);
        }

        mRenderView.addRenderCallback(mSHCallback);
        mRenderView.setVideoRotation(mVideoRotationDegree);
    }

    public View getRenderView() {
        if (null!=mRenderView){
            return mRenderView.getView();
        }
        return null;
    }

    public IRenderView getRender() {
        return mRenderView;
    }

    public void switchRoot( ViewGroup root, int render) {
        if (mRenderView != null) {
            //TODO
            if (mMediaPlayer != null)
                mMediaPlayer.setDisplay(null);

            View renderUIView = mRenderView.getView();
            mRenderView.removeRenderCallback(mSHCallback);
            mRenderView = null;
            if (null != mRoot) {
                mRoot.removeView(renderUIView);
            }
        }
        mRoot = root;
        initRenders(render);
    }

    public void switchRoot( ViewGroup root,boolean isVertical) {
        if (mRenderView != null) {
            //TODO

            View renderUIView = mRenderView.getView();
            if (null != mRoot) {
                mRoot.removeView(renderUIView);
            }
            mRoot = root;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER);
            renderUIView.setLayoutParams(lp);
            mRoot.addView(renderUIView,0);
        }
    }

    public void setUseTextureView(boolean useTextureView) {
        isUseTextureView = useTextureView;
    }

    //private int mCurrentAspectRatioIndex = 0;
    private int mCurrentAspectRatio = IRenderView.AR_MATCH_PARENT;

    /**
     * Sets video path.
     *
     * @param path the path of the video.
     */
    public void setVideoPath(String path, int seekPosition) {
        setVideoURI(Uri.parse(path), seekPosition);
    }

    /**
     * Sets video URI.
     *
     * @param uri the URI of the video.
     */
    public void setVideoURI(Uri uri, int seekPosition) {
        setVideoURI(uri, null, seekPosition);
    }

    /**
     * Sets video URI using specific headers.
     *
     * @param uri     the URI of the video.
     * @param headers the headers for the URI request.
     *                Note that the cross domain redirection is allowed by default, but that can be
     *                changed with key/value pairs through the headers parameter with
     *                "android-allow-cross-domain-redirect" as the key and "0" or "1" as the value
     *                to disallow or allow cross domain redirection.
     */
    private void setVideoURI(Uri uri, Map<String, String> headers, int seekPosition) {
        mUri = uri;
        mHeaders = headers;
        mSeekWhenPrepared = seekPosition;
        openVideo();
    }

    @SuppressLint("ObsoleteSdkInt")
    private void openVideo() {
        Log.i("IJKPlayer","openVideo#begin");
        if (mUri == null || mSurfaceHolder == null) {
            // not ready for playback just yet, will try again later
            return;
        }
        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release(false);

        AudioManager am = (AudioManager) mAppContext.getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        try {
            mMediaPlayer = createPlayer(Constant.IJK_MEDIA_PLAYER);
            // REMOVED: mAudioSession
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mMediaPlayer.setOnSeekCompleteListener(mSeekCompleteListener);
            mCurrentBufferPercentage = 0;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                mMediaPlayer.setDataSource(mAppContext, mUri, mHeaders);
            } else {
                mMediaPlayer.setDataSource(mUri.toString());
            }
            bindSurfaceHolder(mMediaPlayer, mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mPrepareStartTime = System.currentTimeMillis();
            mMediaPlayer.prepareAsync();

            mCurrentState = STATE_PREPARING;
        } catch (IOException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        } finally {
            // REMOVED: mPendingSubtitleTracks.clear();
        }
    }


    public IMediaPlayer createPlayer(int playerType) {
        IMediaPlayer mediaPlayer = null;

        switch (playerType) {

            case Constant.PV_PLAYER__AndroidMediaPlayer: {
                Trace.Info(TAG + " AndroidMediaPlayer");
                AndroidMediaPlayer androidMediaPlayer = new AndroidMediaPlayer();
                mediaPlayer = androidMediaPlayer;
            }
            break;
            case Constant.PV_PLAYER__IjkMediaPlayer:
                Trace.Info(TAG + " IjkMediaPlayer");
            default: {
                IjkMediaPlayer ijkMediaPlayer = null;
                if (mUri != null) {
                    ijkMediaPlayer = new IjkMediaPlayer();
                    IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_FATAL);

                    if (Constant.IJK_MEDIA_CODEC) {
                        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
                        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1);
                    } else {
                        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0);
                    }

                    if (Constant.IJK_OPENSLES) {
                        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 1);
                    } else {
                        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0);
                    }
                    ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 0);
                    ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32);
                    ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1);
                    ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);
                    ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);
                    ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);
                }
                mediaPlayer = ijkMediaPlayer;
            }
            break;
        }

        return mediaPlayer;
    }

    public void releaseWithoutStop() {
        if (mMediaPlayer != null)
            mMediaPlayer.setDisplay(null);
    }

    public void release(boolean cleartargetstate) {
        Trace.Info(TAG + " release");
        Trace.Info(TAG + " mMediaPlay = " + mMediaPlayer);
        if (mMediaPlayer != null) {
            if (cleartargetstate) {
                Trace.Info(TAG + " isInPlaybackState = " + isInPlaybackState());
                mSeekWhenPrepared = getCurrentPosition();
                Trace.Info(TAG + " mSeekWhenPrepared = " + mSeekWhenPrepared);
            }

            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            if (cleartargetstate) {
                mTargetState = STATE_IDLE;
            }
            AudioManager am = (AudioManager) mAppContext.getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
        }
    }

    public void bindSurfaceHolder(IMediaPlayer mp, IRenderView.ISurfaceHolder holder) {
        if (mp == null)
            return;

        Trace.Warning(TAG + " holder = " + holder);
        if (holder == null) {
            mp.setDisplay(null);
            return;
        }
        holder.bindToMediaPlayer(mp);
    }

    public void playSeek() {
        Log.i("IJKPlayer","playSeek#begin");
        if (mMediaPlayer != null) {
            if (mSeekWhenPrepared != 0) {
                seekTo(mSeekWhenPrepared);
            }
            start();
        }
    }

    public void play() {
        Log.i("IJKPlayer","play#begin");
        if (mMediaPlayer != null) {
            Trace.Info(TAG + " mMediaPlayer");
            bindSurfaceHolder(mMediaPlayer, mSurfaceHolder);
            //// TODO: 2017-9-4  即时恢复播放
            if(mRenderView instanceof TextureRenderView){//todo:暂时写了TextureRenderView的
                TextureRenderView renderView= (TextureRenderView) mRenderView;
                Log.i("IJKPlayer","play#surface not null!!!!!!!!!!!!!!!!!!!!! set it!");
                if(renderView.mSurfaceCallback!=null && renderView.mSurfaceCallback.mSurfaceTexture!=null) {
                    //todo:恢复surface
                    if(renderView.getSurfaceTexture() == null) {
                        //todo:判断作用——有的界面（怀疑也是用textureview导致）显示在前台，但是不会触发surfaceDestory，这里判断下，否则，会有  queueBuffer: BufferQueue has been abandoned!  的错误。
                        Log.i("IJKPlayer", "play#surface not null!!!!!!!!!!!!!!!!!!!!! set it!");
                        renderView.setSurfaceTexture(renderView.mSurfaceCallback.mSurfaceTexture);
                    }
                }
                else if(getCurrentPosition()!=0){
                    playSeek();
                    return;
                }
                start();
            }

        } else
            openVideo();
    }

    IRenderView.IRenderCallback mSHCallback = new IRenderView.IRenderCallback() {
        @Override
        public void onSurfaceChanged(@NonNull IRenderView.ISurfaceHolder holder, int format, int w, int h) {
            if (holder.getRenderView() != mRenderView) {
                Trace.Warning(TAG + "onSurfaceChanged: unmatched render callback\n" + holder.getRenderView());
                return;
            }
            Trace.Info(TAG + " onSurfaceChanged " + holder.getRenderView());
            mSurfaceWidth = w;
            mSurfaceHeight = h;
            boolean isValidState = (mTargetState == STATE_PLAYING || mTargetState == STATE_PAUSED);
            boolean hasValidSize = !mRenderView.shouldWaitForResize() || (mVideoWidth == w && mVideoHeight == h);
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if(isFirst && mTargetState != STATE_PAUSED){
                    start();
                    isFirst = false;
                }

            }
        }

        @Override
        public void onSurfaceCreated(@NonNull IRenderView.ISurfaceHolder holder, int width, int height) {
            if (holder.getRenderView() != mRenderView) {
                Trace.Warning(TAG + "onSurfaceCreated: unmatched render callback\n" + holder.getRenderView());
                return;
            }
            Trace.Info(TAG + " onSurfaceCreated " + holder.getRenderView());
            mSurfaceHolder = holder;
            if (mMediaPlayer != null) {
                Trace.Info(TAG + " mMediaPlayer");
                bindSurfaceHolder(mMediaPlayer, holder);
            } else
                openVideo();
        }

        @Override
        public void onSurfaceDestroyed(@NonNull IRenderView.ISurfaceHolder holder) {

            if (holder.getRenderView() != mRenderView) {
                Trace.Warning(TAG + "onSurfaceDestroyed: unmatched render callback\n" + holder.getRenderView());
                return;
            }
            Trace.Info(TAG + " onSurfaceDestroyed " + holder.getRenderView());
            Trace.Info(TAG + " mMediaPlayer = " + mMediaPlayer);
            // after we return from this we can't use the surface any more
            //mSurfaceHolder = null;
            //releaseWithoutStop();
            if (mMediaPlayer != null) {
                Trace.Info(TAG + " isInPlaybackState = " + isInPlaybackState());
                mSeekWhenPrepared = getCurrentPosition();
                Trace.Info(TAG + " mSeekWhenPrepared = " + mSeekWhenPrepared);
            }
            pause();
        }
    };

    IMediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new IMediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
                    Log.i("IJKPlayer","onVideoSizeChanged#begin");
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();
                    mVideoSarNum = mp.getVideoSarNum();
                    mVideoSarDen = mp.getVideoSarDen();
                    Log.i("IJKPlayer","onVideoSizeChanged#mVideoWidth:"+mVideoWidth+"/mVideoHeight:"+mVideoHeight);
                    if (mVideoWidth != 0 && mVideoHeight != 0) {
                        if (mRenderView != null) {
                            mRenderView.setVideoSize(mVideoWidth, mVideoHeight);
                            mRenderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);
                        }
                        // REMOVED: getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                        //requestLayout();
                    }
                }
            };

    IMediaPlayer.OnPreparedListener mPreparedListener = new IMediaPlayer.OnPreparedListener() {
        public void onPrepared(IMediaPlayer mp) {
            Log.i("IJKPlayer","onPrepared#begin");
            mPrepareEndTime = System.currentTimeMillis();
            //mHudViewHolder.updateLoadCost(mPrepareEndTime - mPrepareStartTime);
            mCurrentState = STATE_PREPARED;

            // Get the capabilities of the player for this stream
            // REMOVED: Metadata

            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(mMediaPlayer);
            }
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();
//            mSeekWhenPrepared = (int) (mp.getDuration() - 10000);
            int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
            Trace.Info(TAG + " mSeekWhenPrepared = " + mSeekWhenPrepared);
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }
            Trace.Info(TAG + " mVideoWidth = " + mVideoWidth);
            Trace.Info(TAG + " mVideoHeight = " + mVideoHeight);
            Trace.Info(TAG + " mTargetState = " + mTargetState);
            if (mVideoWidth != 0 && mVideoHeight != 0) {
                //Log.i("@@@@", "video size: " + mVideoWidth +"/"+ mVideoHeight);
                Log.i("IJKPlayer","onPrepared#has video size");
                // REMOVED: getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                if (mRenderView != null) {
                    Log.i("IJKPlayer","onPrepared#mRenderView != null");
                    mRenderView.setVideoSize(mVideoWidth, mVideoHeight);
                    mRenderView.setVideoSampleAspectRatio(mVideoSarNum, mVideoSarDen);
                    if (!mRenderView.shouldWaitForResize() || mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) {
                        // We didn't actually change the size (it was already at the size
                        // we need), so we won't get a "surface changed" callback, so
                        // start the video here instead of in the callback.
                        Log.i("IJKPlayer","onPrepared#bulabula");
                        if (mTargetState == STATE_PLAYING || mTargetState == STATE_ERROR) {
                            Log.i("IJKPlayer","onPrepared#start!!!has video size!");
                            start();
                        } else if (!isPlaying() &&
                                (seekToPosition != 0 || getCurrentPosition() > 0)) {
                            Log.i("IJKPlayer","onPrepared#what?");
                        }
                        else{
                            Log.i("IJKPlayer","onPrepared#custom play");
                           // pause();
                        }
                    }
                }
            } else {
                // We don't know the video size yet, but should start anyway.
                // The video size might be reported to us later.
                if (mTargetState == STATE_PLAYING) {
                    Log.i("IJKPlayer","onPrepared#start!!!no video size!");
                    start();
                }
            }
        }
    };

    private IMediaPlayer.OnCompletionListener mCompletionListener =
            new IMediaPlayer.OnCompletionListener() {
                public void onCompletion(IMediaPlayer mp) {
                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;

                    if (mOnCompletionListener != null) {
                        mOnCompletionListener.onCompletion(mMediaPlayer);
                    }
                }
            };

    private IMediaPlayer.OnInfoListener mInfoListener =
            new IMediaPlayer.OnInfoListener() {
                public boolean onInfo(IMediaPlayer mp, int arg1, int arg2) {
                    if (mOnInfoListener != null) {
                        mOnInfoListener.onInfo(mp, arg1, arg2);
                    }
                    switch (arg1) {
                        case IMediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                            Log.d(TAG, "MEDIA_INFO_VIDEO_TRACK_LAGGING:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                            Log.d(TAG, "MEDIA_INFO_VIDEO_RENDERING_START:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                            Log.d(TAG, "MEDIA_INFO_BUFFERING_START:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                            Log.d(TAG, "MEDIA_INFO_BUFFERING_END:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH:
//                            Log.d(TAG, "MEDIA_INFO_NETWORK_BANDWIDTH: " + arg2);
                            break;
                        case IMediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                            Log.d(TAG, "MEDIA_INFO_BAD_INTERLEAVING:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                            Log.d(TAG, "MEDIA_INFO_NOT_SEEKABLE:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                            Log.d(TAG, "MEDIA_INFO_METADATA_UPDATE:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE:
                            Log.d(TAG, "MEDIA_INFO_UNSUPPORTED_SUBTITLE:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT:
                            Log.d(TAG, "MEDIA_INFO_SUBTITLE_TIMED_OUT:");
                            break;
                        case IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED:
                            mVideoRotationDegree = arg2;
                            Log.d(TAG, "MEDIA_INFO_VIDEO_ROTATION_CHANGED: " + arg2);
                            if (mRenderView != null)
                                mRenderView.setVideoRotation(arg2);
                            break;
                        case IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START:
                            Log.d(TAG, "MEDIA_INFO_AUDIO_RENDERING_START:");
                            break;
                    }
                    return true;
                }
            };

    private IMediaPlayer.OnErrorListener mErrorListener =
            new IMediaPlayer.OnErrorListener() {
                public boolean onError(IMediaPlayer mp, int framework_err, int impl_err) {
                    Log.d(TAG, "Error: " + framework_err + "," + impl_err);
                    mCurrentState = STATE_ERROR;
                    mTargetState = STATE_ERROR;

                    /* If an error handler has been supplied, use it and finish. */
                    if (mOnErrorListener != null) {
                        if (mOnErrorListener.onError(mMediaPlayer, framework_err, impl_err)) {
                            return true;
                        }
                    }

                    /* Otherwise, pop up an error dialog so the user knows that
                     * something bad has happened. Only try and pop up the dialog
                     * if we're attached to a window. When we're going away and no
                     * longer have a window, don't bother showing the user an error.
                     */
                    //TODO 显示错误弹窗
                    return true;
                }
            };

    private IMediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            new IMediaPlayer.OnBufferingUpdateListener() {
                public void onBufferingUpdate(IMediaPlayer mp, int percent) {
                    mCurrentBufferPercentage = percent;
                }
            };

    private IMediaPlayer.OnSeekCompleteListener mSeekCompleteListener = new IMediaPlayer.OnSeekCompleteListener() {

        @Override
        public void onSeekComplete(IMediaPlayer mp) {
            mSeekEndTime = System.currentTimeMillis();
            Trace.Info(TAG + " mSeekEndTime = " + mSeekEndTime);
            //mHudViewHolder.updateSeekCost(mSeekEndTime - mSeekStartTime);
        }
    };

    /**
     * Register a callback to be invoked when the media file
     * is loaded and ready to go.
     *
     * @param l The callback that will be run
     */
    public void setOnPreparedListener(IMediaPlayer.OnPreparedListener l) {
        mOnPreparedListener = l;
    }

    /**
     * Register a callback to be invoked when the end of a media file
     * has been reached during playback.
     *
     * @param l The callback that will be run
     */
    public void setOnCompletionListener(IMediaPlayer.OnCompletionListener l) {
        mOnCompletionListener = l;
    }

    /**
     * Register a callback to be invoked when an error occurs
     * during playback or setup.  If no listener is specified,
     * or if the listener returned false, VideoView will inform
     * the user of any errors.
     *
     * @param l The callback that will be run
     */
    public void setOnErrorListener(IMediaPlayer.OnErrorListener l) {
        mOnErrorListener = l;
    }

    /**
     * Register a callback to be invoked when an informational event
     * occurs during playback or setup.
     *
     * @param l The callback that will be run
     */
    public void setOnInfoListener(IMediaPlayer.OnInfoListener l) {
        mOnInfoListener = l;
    }

    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;

            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
            AudioManager am = (AudioManager) mAppContext.getSystemService(Context.AUDIO_SERVICE);
            am.abandonAudioFocus(null);
        }
    }


    @Override
    public void start() {
        Trace.Info(TAG + " start ## isInPlaybackState:"+isInPlaybackState());

        if (isInPlaybackState()) {
            mMediaPlayer.start();
            Trace.Info(TAG + " mCurrentState = " + mCurrentState);
            mCurrentState = STATE_PLAYING;
            Trace.Info(TAG + " mCurrentState = " + mCurrentState);
            mTargetState = STATE_PLAYING;
        }
        if(!(mTargetState == STATE_PAUSED)){
            mTargetState = STATE_PLAYING;
        }
    }

    @Override
    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                Trace.Info(TAG + " mCurrentState = " + mCurrentState);
                mCurrentState = STATE_PAUSED;
                Trace.Info(TAG + " mCurrentState = " + mCurrentState);
            }
        }
        mTargetState = STATE_PAUSED;
        Trace.Info(TAG + " mTargetState = " + mTargetState);
    }

    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            return (int) mMediaPlayer.getDuration();
        }

        return -1;
    }

    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return (int) mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public void seekTo(int pos) {

        Log.i("IJKPlayer","seekTo#begin:"+isInPlaybackState());
        if (isInPlaybackState()) {
            try{
                mSeekStartTime = System.currentTimeMillis();
                mMediaPlayer.seekTo(pos);
                mSeekWhenPrepared = 0;
            }catch (IllegalStateException e){
                e.printStackTrace();
            }

        } else {
            mSeekWhenPrepared = pos;
        }
    }

    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    @Override
    public boolean canPause() {
        return mCanPause;
    }

    @Override
    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    @Override
    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    public boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING);
    }

    public int getCurrentState() {
        return mCurrentState;
    }

    public void setCurrentSeekPosition(int seekPosition) {
        mSeekWhenPrepared = seekPosition;

    }

    public int getmTargetState() {
        return mTargetState;
    }

    public void onCreate() {
        initRenders(Constant.IJK_RENDER_VIEW);

        mVideoWidth = 0;
        mVideoHeight = 0;

        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
    }


    public Uri getmUri() {
        return mUri;
    }

    public void onStart() {

    }

    public void onResume() {

    }

    public void onPause() {

    }

    public void onStop() {

    }

    public void onDestroy() {

    }

    public void resumePlay(int seekPosition) {
        mSeekWhenPrepared = seekPosition;
        openVideo();
    }

    public void destory(){
        release(true);
        if(mRenderView!=null){
            if(mRenderView instanceof TextureRenderView){
                TextureRenderView renderView = (TextureRenderView) mRenderView;
                renderView.mSurfaceCallback.releaseSurfaceTexture(renderView.mSurfaceCallback.mSurfaceTexture);

            }
        }
    }
}
