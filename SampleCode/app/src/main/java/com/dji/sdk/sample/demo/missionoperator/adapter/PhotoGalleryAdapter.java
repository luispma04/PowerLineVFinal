package com.dji.sdk.sample.demo.missionoperator.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.demo.missionoperator.util.PhotoStorageManager;

import java.util.List;

/**
 * Adaptador para exibir fotos em um RecyclerView
 */
public class PhotoGalleryAdapter extends RecyclerView.Adapter<PhotoGalleryAdapter.PhotoViewHolder> {

    private static final String TAG = "PhotoGalleryAdapter";
    private List<PhotoStorageManager.PhotoInfo> photoList;
    private Context context;
    private OnPhotoClickListener photoClickListener;

    public interface OnPhotoClickListener {
        void onPhotoClick(PhotoStorageManager.PhotoInfo photoInfo);
        void onDownloadClick(PhotoStorageManager.PhotoInfo photoInfo);
        void onDeleteClick(PhotoStorageManager.PhotoInfo photoInfo);
    }

    public PhotoGalleryAdapter(Context context, List<PhotoStorageManager.PhotoInfo> photoList, OnPhotoClickListener listener) {
        this.context = context;
        this.photoList = photoList;
        this.photoClickListener = listener;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_photo_gallery, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        if (position >= photoList.size()) {
            Log.e(TAG, "onBindViewHolder: position out of bounds");
            return;
        }

        final PhotoStorageManager.PhotoInfo photoInfo = photoList.get(position);
        if (photoInfo == null || photoInfo.getFile() == null) {
            Log.e(TAG, "onBindViewHolder: photoInfo is null");
            return;
        }

        try {
            // Carregar a miniatura
            if (photoInfo.getFile().exists()) {
                Bitmap thumbnail = BitmapFactory.decodeFile(photoInfo.getFile().getAbsolutePath());
                holder.photoImageView.setImageBitmap(thumbnail);
            } else {
                Log.e(TAG, "File doesn't exist: " + photoInfo.getFile().getAbsolutePath());
                holder.photoImageView.setImageResource(R.drawable.rounded_card_bg); // Imagem de fallback
            }

            // Configurar as informações da foto
            holder.structureIdText.setText("Estrutura: " + photoInfo.getStructureId());
            holder.photoIdText.setText("Foto: " + photoInfo.getPhotoId());
            holder.timestampText.setText(photoInfo.getTimestamp());

            // Definir os listeners dos botões
            holder.photoImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (photoClickListener != null) {
                        photoClickListener.onPhotoClick(photoInfo);
                    }
                }
            });

            holder.downloadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (photoClickListener != null) {
                        photoClickListener.onDownloadClick(photoInfo);
                    }
                }
            });

            holder.deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Delete button clicked for: " + photoInfo.getFilename());
                    if (photoClickListener != null) {
                        photoClickListener.onDeleteClick(photoInfo);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error binding view holder: " + e.getMessage(), e);
        }
    }

    @Override
    public int getItemCount() {
        return photoList != null ? photoList.size() : 0;
    }

    /**
     * Atualiza a lista de fotos e notifica o adaptador
     * @param photos A nova lista de fotos
     */
    public void updatePhotoList(List<PhotoStorageManager.PhotoInfo> photos) {
        this.photoList = photos;
        notifyDataSetChanged();
    }

    public static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView photoImageView;
        TextView structureIdText;
        TextView photoIdText;
        TextView timestampText;
        Button downloadButton;
        Button deleteButton;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            photoImageView = itemView.findViewById(R.id.img_photo_thumbnail);
            structureIdText = itemView.findViewById(R.id.text_structure_id);
            photoIdText = itemView.findViewById(R.id.text_photo_id);
            timestampText = itemView.findViewById(R.id.text_timestamp);
            downloadButton = itemView.findViewById(R.id.btn_download_photo);
            deleteButton = itemView.findViewById(R.id.btn_delete_photo);
        }
    }
}