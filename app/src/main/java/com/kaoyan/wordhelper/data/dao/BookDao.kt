package com.kaoyan.wordhelper.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kaoyan.wordhelper.data.entity.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM tb_book ORDER BY type ASC, id ASC")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM tb_book ORDER BY type ASC, id ASC")
    suspend fun getAllBooksList(): List<Book>

    @Query("SELECT * FROM tb_book WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveBook(): Book?

    @Query("SELECT * FROM tb_book WHERE is_active = 1 LIMIT 1")
    fun getActiveBookFlow(): Flow<Book?>

    @Query("SELECT * FROM tb_book WHERE type = 2 LIMIT 1")
    suspend fun getNewWordsBook(): Book?

    @Query("SELECT * FROM tb_book WHERE id = :bookId")
    suspend fun getBookById(bookId: Long): Book?

    @Query("SELECT * FROM tb_book WHERE name = :name AND type = :type LIMIT 1")
    suspend fun findByNameAndType(name: String, type: Int): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)

    @Query("UPDATE tb_book SET is_active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE tb_book SET is_active = 1 WHERE id = :bookId")
    suspend fun activate(bookId: Long)

    @Query("UPDATE tb_book SET total_count = :count WHERE id = :bookId")
    suspend fun updateTotalCount(bookId: Long, count: Int)
}
