package com.novelreader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    val books: StateFlow<List<BookEntity>> = repository.allBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _processingState = MutableStateFlow(ProcessingState())
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun addBook(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _processingState.value = ProcessingState(isProcessing = true)
            try {
                repository.addBook(uri, onProgress = { step, stepLocalPercent, phase ->
                    _processingState.value = ProcessingState(true, step, 4, stepLocalPercent, phase)
                }).onFailure { e ->
                    _errorMessage.value = e.message ?: "PDF処理に失敗しました"
                }
            } finally {
                _processingState.value = ProcessingState()
            }
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteBook(book) }
    }

    suspend fun getLastRead(bookId: String): String? = repository.getLastRead(bookId)

    fun saveProgress(bookId: String, filename: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.saveProgress(bookId, filename) }
    }

    fun clearError() { _errorMessage.value = null }
}
