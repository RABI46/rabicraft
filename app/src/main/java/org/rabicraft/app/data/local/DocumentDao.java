package org.rabicraft.app.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY lastOpenedTimestamp DESC")
    List<DocumentEntity> getAllDocuments();

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    DocumentEntity getDocumentById(String id);

    @Query("SELECT * FROM documents WHERE groupName = :groupName ORDER BY lastOpenedTimestamp DESC")
    List<DocumentEntity> getDocumentsByGroup(String groupName);

    @Query("SELECT * FROM documents WHERE isStarred = 1 ORDER BY lastOpenedTimestamp DESC")
    List<DocumentEntity> getStarredDocuments();

    @Query("SELECT DISTINCT groupName FROM documents ORDER BY groupName ASC")
    List<String> getDistinctGroups();

    @Query("SELECT * FROM documents WHERE name LIKE '%' || :query || '%' OR tagsCsv LIKE '%' || :query || '%' ORDER BY lastOpenedTimestamp DESC")
    List<DocumentEntity> searchDocuments(String query);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDocument(DocumentEntity document);

    @Update
    void updateDocument(DocumentEntity document);

    @Query("UPDATE documents SET name = :newName WHERE id = :id")
    void renameDocument(String id, String newName);

    @Query("UPDATE documents SET groupName = :newGroup WHERE id = :id")
    void updateDocumentGroup(String id, String newGroup);

    @Query("UPDATE documents SET tagsCsv = :newTags WHERE id = :id")
    void updateDocumentTags(String id, String newTags);

    @Query("UPDATE documents SET isStarred = :isStarred WHERE id = :id")
    void setDocumentStarred(String id, boolean isStarred);

    @Query("UPDATE documents SET lastOpenedTimestamp = :timestamp WHERE id = :id")
    void updateLastOpened(String id, long timestamp);

    @Query("DELETE FROM documents WHERE id = :id")
    void deleteDocumentById(String id);
}
