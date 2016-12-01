package me.dong.demo_multitrack;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;


/**
 * Created by Dong on 2016-11-28.
 */

public class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.VideoViewHolder> {

    private List<MediaStoreData> mMediaStoreDataList;
    private Context mContext;

    public VideoListAdapter(Context context, List<MediaStoreData> datas) {
        mMediaStoreDataList = datas;
        mContext = context;
    }

    @Override
    public VideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_list, parent, false);
        return new VideoViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(VideoViewHolder holder, int position) {
        final MediaStoreData current = mMediaStoreDataList.get(position);

        holder.tvVideoTitle.setText(current.displayName);
        holder.tvVideoUri.setText(current.uri.toString());
        holder.tvMimeType.setText(current.mimeType);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(mContext, PlayerActivity.class);
                intent.putExtra("uri", current.uri);
                intent.putExtra("displayName", current.displayName);
                intent.putExtra("mimeType", current.mimeType);
                mContext.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mMediaStoreDataList.size();
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {

        TextView tvVideoTitle;
        TextView tvVideoUri;
        TextView tvMimeType;

        public VideoViewHolder(View itemView) {
            super(itemView);

            tvVideoTitle = (TextView) itemView.findViewById(R.id.tv_video_title);
            tvVideoUri = (TextView) itemView.findViewById(R.id.tv_video_uri);
            tvMimeType = (TextView) itemView.findViewById(R.id.tv_video_mimetype);
        }
    }
}
