package org.rabicraft.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Entity(tableName = "documents")
public class DocumentEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String name;
    public String originalFileName;
    public String filePath;
    public long fileSizeBytes;
    public int pageCount;
    public String groupName;
    public String tagsCsv;
    public boolean isStarred;
    public long lastOpenedTimestamp;
    public long createdAtTimestamp;
    public boolean isSample;

    public DocumentEntity(
            @NonNull String id,
            String name,
            String originalFileName,
            String filePath,
            long fileSizeBytes,
            int pageCount,
            String groupName,
            String tagsCsv,
            boolean isStarred,
            long lastOpenedTimestamp,
            long createdAtTimestamp,
            boolean isSample
    ) {
        this.id = id;
        this.name = name != null ? name : "";
        this.originalFileName = originalFileName != null ? originalFileName : "";
        this.filePath = filePath != null ? filePath : "";
        this.fileSizeBytes = fileSizeBytes;
        this.pageCount = pageCount > 0 ? pageCount : 1;
        this.groupName = groupName != null && !groupName.trim().isEmpty() ? groupName.trim() : "General";
        this.tagsCsv = tagsCsv != null ? tagsCsv : "";
        this.isStarred = isStarred;
        this.lastOpenedTimestamp = lastOpenedTimestamp;
        this.createdAtTimestamp = createdAtTimestamp;
        this.isSample = isSample;
    }

    public List<String> getTagsList() {
        if (tagsCsv == null || tagsCsv.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = tagsCsv.split(",");
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }
}
