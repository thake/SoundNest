package io.github.thake.soundnest.core.catalog

sealed class Response<T> {

    class Loading<T> : Response<T>()
    data class Success<T>(val data: T) : Response<T>()
    data class Error<T>(val error : String) : Response<T>()
}