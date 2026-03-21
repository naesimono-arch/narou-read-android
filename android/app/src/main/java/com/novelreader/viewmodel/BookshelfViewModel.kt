package com.novelreader.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelreader.NovelReaderApplication
import com.novelreader.PdfProcessingService
import com.novelreader.data.BookEntity
import com.novelreader.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProcessingState(
    val isProcessing: Boolean = false,
    val stepIndex: Int = 0,
    val stepTotal: Int = 4,
    val stepLocalPercent: Float = 0f,
    val phase: String = "",
)

class BookshelfViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(application)
    private val app = application as NovelReaderApplication

    val books: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Application の StateFlow を購読して processingState を提供
    val processingState: StateFlow<ProcessingState> = app.processingState
        .map { it ?: ProcessingState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProcessingState())

    val errorMessage: StateFlow<String?> = app.errorState.asStateFlow()

    fun addBook(uri: Uri) {
        val intent = Intent(getApplication(), PdfProcessingService::class.java).apply {
            action = PdfProcessingService.ACTION_START
            data = uri
            // content:// URI の読み取り権限を Service に委譲
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteBook(book) }
    }

    suspend fun getLastRead(bookId: String): String? = repository.getLastRead(bookId)

    fun saveProgress(bookId: String, filename: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.saveProgress(bookId, filename) }
    }

    fun clearError() { app.errorState.value = null }
}
