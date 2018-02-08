package sugoi.android.kpopkoreandramawallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import android.support.v4.util.LruCache;


import static android.provider.ContactsContract.CommonDataKinds.Website.URL;

/**
 * Created by Adil on 12-01-2018.
 */
public class ThumbnailDownloader<T> extends HandlerThread {

    Context context;
    private static final String TAG =
            "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;
    private Handler mRequestHandler;
    private ConcurrentMap<T, String> mRequestMap = new
            ConcurrentHashMap<>();
    private Handler mResponseHandler;
    private static final int MESSAGE_PRELOAD = 1;
    private ThumbnailDownloadListener<T> mThumbnailDownloadListener;
    private static final int CACHE_SIZE = 400;
    LruCache<String, Bitmap> mCache;



    public interface ThumbnailDownloadListener<T> {
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    public void setThumbnailDownloadListener(ThumbnailDownloadListener<T> listener) {
        mThumbnailDownloadListener = listener;
    }





    private boolean mHasQuit = false;

    public ThumbnailDownloader(Handler response) {
        super(TAG);
        mResponseHandler = response;
        mCache = new LruCache<String, Bitmap>(CACHE_SIZE);
    }

    @Override
    protected void onLooperPrepared() {
        mRequestHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    @SuppressWarnings("unchecked")
                    T token = (T)msg.obj;
                    Log.i(TAG, "Got a request for url: " + mRequestMap.get(token));
                    handleRequest(token);
                } else if(msg.what == MESSAGE_PRELOAD) {
                    String url = (String)msg.obj;
                    preload(url);
                }
            }
        };
    }

    private void handleRequest(final T target) {
            final String url =
                    mRequestMap.get(target);
            if (url == null) {
                return;
            }

            if (mCache.get(url) == null)
                preload(target);
            final Bitmap bitmap = mCache.get(url);


            mResponseHandler.post(new Runnable() {
                public void run() {
                    if (mRequestMap.get(target) != url || mHasQuit) {
                        return;
                    }
                    mRequestMap.remove(target);
                    mThumbnailDownloadListener.onThumbnailDownloaded(target, bitmap);
                }
            });

    }

    @Override
    public boolean quit() {
        mHasQuit = true;
        return super.quit();
    }

    public void queueThumbnail(T target, String url) {
        Log.i(TAG, "Got a URL: " + url);

        mRequestMap.put(target, url);

        mRequestHandler
                .obtainMessage(MESSAGE_DOWNLOAD, target)
                .sendToTarget();

    }


    public void clearQueue() {
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
        mRequestMap.clear();
    }


    public void queuePreload(String url) {
        if (mCache.get(url) != null) return;

        mRequestHandler
                .obtainMessage(MESSAGE_PRELOAD, url)
                .sendToTarget();
    }

    public Bitmap checkCache(String url) {
        return mCache.get(url);
    }

    private Bitmap getBitmap(String url) {

        try {
            byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
            Bitmap bitmapDecode = BitmapFactory
                    .decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            Log.i(TAG, "bitmap created");
            return bitmapDecode;
        } catch (IOException ioe) {
            Log.e(TAG, "Error downloading image", ioe);
        }
        return null;
    }

    private void preload(final T token) {
        String url = mRequestMap.get(token);
        preload(url);
    }

    private void preload(String url) {
        if (url == null)
            return;
        if (mCache.get(url) != null)
            return;
        Bitmap bitmap = getBitmap(url);
        if (bitmap != null)
            mCache.put(url, bitmap);

    }

}