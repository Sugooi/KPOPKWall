package sugoi.android.kpopkoreandramawallpaper;

import android.annotation.TargetApi;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static android.content.ContentValues.TAG;

/**
 * Created by Adil on 09-01-2018.
 */

public class PhotoGalleryFragment extends Fragment {
    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private ThumbnailDownloader<ImageView> mThumbnailDownloader;
    Handler responseHandler = new Handler();
    private boolean loading = false;
    private int mPage = 0;




    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);



        setHasOptionsMenu(true);
        loadData();

        Intent i = PollService.newIntent(getActivity());
        getActivity().startService(i);

        responseHandler = new Handler();


        mThumbnailDownloader  = new ThumbnailDownloader<ImageView>(new Handler());

        mThumbnailDownloader.setThumbnailDownloadListener(new ThumbnailDownloader.ThumbnailDownloadListener<ImageView>(){
            @Override
            public void onThumbnailDownloaded(ImageView imageView, Bitmap thumbnail) {
                if (isVisible()) {
                    imageView.setImageBitmap(thumbnail);
                }
            }
        });


        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background thread started");
    }




    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {


        super.onCreateOptionsMenu(menu,
                inflater);
        inflater.inflate(R.menu.fragment_photo,
                menu);


        MenuItem searchItem =
                menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new
                                                  SearchView.OnQueryTextListener() {
                                                      @Override
                                                      public boolean onQueryTextSubmit(String s) {
                                                          Log.d("In Single Fragment", "QueryTextSubmit: " + s);
                                                          query= s;
                                                          updateItems();
                                                          return true;
                                                      }
                                                      @Override
                                                      public boolean onQueryTextChange(String s) {
                                                          Log.d("In Single Fragment", "QueryTextChange: " + s);
                                                          return false;
                                                      }
                                                  });
    }

    private void updateItems() {
        new FetchItemsTask().execute();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        setHasOptionsMenu(true);


    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case R.id.action_search:
                //doing stuff
                return true;
        }
        return false;
    }

    private void loadData() {
        if(loading)return;
        loading = true;
        mPage++;
        new FetchItemsTask().execute();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_photo_gallery, container,false);

        mPhotoRecyclerView =(RecyclerView) v.findViewById(R.id.photo_recycler_view);
        mPhotoRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(),3));

        setupAdapter();

        return v;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    private void setupAdapter() {
        if (isAdded()) {
            mPhotoRecyclerView.setAdapter(new
                    PhotoAdapter(mItems));
        }
    }

    private class PhotoHolder extends
            RecyclerView.ViewHolder {
        private ImageView mItemImageView;
        public PhotoHolder(View itemView) {
            super(itemView);
            mItemImageView = (ImageView) itemView.findViewById(R.id.item_image_view);
        }
        public void bindDrawable(Drawable drawable) {
            mItemImageView.setImageDrawable(drawable);
        }
        public ImageView returnimageView(){
            return mItemImageView;
        }
    }

    private class PhotoAdapter extends
            RecyclerView.Adapter<PhotoHolder> {
        private List<GalleryItem> mGalleryItems;
        public PhotoAdapter(List<GalleryItem>
                                    galleryItems) {
            mGalleryItems = galleryItems;
        }
        @Override
        public PhotoHolder
        onCreateViewHolder(ViewGroup viewGroup, int viewType)
        {
            LayoutInflater inflater =
                    LayoutInflater.from(getActivity());
            View view =
                    inflater.inflate(R.layout.list_item_gallery, viewGroup,
                            false);
            return new PhotoHolder(view);
        }
        @Override
        public void onBindViewHolder(PhotoHolder
                                             photoHolder, int position) {


            GalleryItem galleryItem =
                    mGalleryItems.get(position);
            Drawable placeholder =
                    getResources().getDrawable(R.drawable.ic_launcher_background);
            photoHolder.bindDrawable(placeholder);

            Bitmap cacheHit = mThumbnailDownloader.checkCache(galleryItem.getmUrl());
            if (cacheHit != null) {
                photoHolder.returnimageView().setImageBitmap(cacheHit);
            } else {
                mThumbnailDownloader.queueThumbnail(photoHolder.returnimageView(), galleryItem.getmUrl());
            }


            for (int i=Math.max(0, position-10); i< Math.min(mItems.size()-1, position+10); i++) {
                mThumbnailDownloader.queuePreload(galleryItem.getmUrl());
            }

        }
        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }
    }

    String query;

    private class FetchItemsTask extends
            AsyncTask<Void,Void,List<GalleryItem>>    {
        @Override
        protected List<GalleryItem> doInBackground(Void... params)
        {
//            try {
//                String result = new FlickrFetchr()
//                        .getUrlString("https://www.bignerdranch.com");
//                Log.i(TAG, "Fetched contents of URL: " + result);
//            } catch (IOException ioe) {
//                Log.e(TAG, "Failed to fetch URL: ",
//                        ioe);
//            }

            if (query == null) {
                return new FlickrFetchr().fetchRecentPhotos();
            } else {
                return new
                        FlickrFetchr().searchPhotos(query);
            }
        }

        @Override
        protected void onPostExecute(List<GalleryItem>
                                             items) {
            mItems = items;
            setupAdapter();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }


}
