package com.dji.sdk.sample.demo.missionoperator.adapter;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.demo.missionoperator.util.PhotoStorageManager;

import java.util.List;

/**
 * Adaptador para exibir pastas de estruturas em um RecyclerView
 */
public class StructureFolderAdapter extends RecyclerView.Adapter<StructureFolderAdapter.FolderViewHolder> {

    private static final String TAG = "StructureFolderAdapter";
    private List<Integer> structureIds;
    private PhotoStorageManager photoStorageManager;
    private Context context;
    private OnStructureClickListener listener;

    public interface OnStructureClickListener {
        void onStructureClick(int structureId);
    }

    public StructureFolderAdapter(Context context, List<Integer> structureIds,
                                  PhotoStorageManager photoStorageManager,
                                  OnStructureClickListener listener) {
        this.context = context;
        this.structureIds = structureIds;
        this.photoStorageManager = photoStorageManager;
        this.listener = listener;
        Log.d(TAG, "Created with " + (structureIds != null ? structureIds.size() : 0) + " structures");
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_structure_folder, parent, false);
        return new FolderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        if (structureIds == null || position >= structureIds.size()) {
            Log.e(TAG, "Invalid position or null structureIds");
            return;
        }

        final int structureId = structureIds.get(position);

        try {
            List<PhotoStorageManager.PhotoInfo> photos = photoStorageManager.getPhotosForStructure(structureId);

            // Definir o título da pasta de estrutura
            holder.titleTextView.setText("Estrutura " + structureId);

            // Definir a contagem de fotos
            int photoCount = photos.size();
            holder.countTextView.setText(photoCount + (photoCount == 1 ? " foto" : " fotos"));

            // Definir o listener do botão de visualização
            holder.viewButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Structure " + structureId + " clicked");
                    if (listener != null) {
                        listener.onStructureClick(structureId);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error binding view holder: " + e.getMessage(), e);
        }
    }

    @Override
    public int getItemCount() {
        return structureIds != null ? structureIds.size() : 0;
    }

    /**
     * Atualiza a lista de estruturas e notifica o adaptador
     * @param structureIds Nova lista de IDs de estrutura
     */
    public void updateStructureList(List<Integer> structureIds) {
        this.structureIds = structureIds;
        Log.d(TAG, "Updated with " + (structureIds != null ? structureIds.size() : 0) + " structures");
        notifyDataSetChanged();
    }

    public static class FolderViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView countTextView;
        Button viewButton;

        public FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.text_structure_folder_title);
            countTextView = itemView.findViewById(R.id.text_photos_count);
            viewButton = itemView.findViewById(R.id.btn_view_structure_photos);
        }
    }
}