package com.jetbrains.bigdatatools.kafka.core.rfs.driver.fileinfo

import com.jetbrains.bigdatatools.kafka.core.rfs.driver.DriverException

sealed class SafeResult<T> {
  abstract val result: T?
  abstract val exception: Throwable?

  fun resultOrThrow(): T = when (this) {
    is ErrorResult -> this.throwException()
    is OkResult -> this.result
  }

  fun resultOrThrowWithWrapper(wrapper: String): T = when (this) {
    is ErrorResult -> throw DriverException(wrapper, this.exception)
    is OkResult -> this.result
  }

  fun <K> map(f: (T) -> K): SafeResult<K> = when (this) {
    is ErrorResult -> ErrorResult(exception)
    is OkResult -> {
      try {
        val res = f(result)
        OkResult(res)
      }
      catch (t: Throwable) {
        ErrorResult(t)
      }
    }
  }

  inline fun getOrElse(onFailure: (exception: Throwable) -> T): T {
    return when (this) {
      is OkResult -> this.result
      is ErrorResult -> onFailure(this.exception)
    }
  }

  companion object {
    @Suppress("NOTHING_TO_INLINE")
    inline fun rethrowException(exception: Throwable): Nothing {
      exception.stackTrace += Thread.currentThread().stackTrace
      throw exception
    }
  }

}

data class OkResult<T>(override val result: T) : SafeResult<T>() {
  override val exception: Throwable? get() = null
}
class ErrorResult<T>(override val exception: Throwable) : SafeResult<T>() {
  override val result: T? get() = null

  fun <R> throwException(): R {
    rethrowException(exception)
  }
}

fun <T> SafeResult<T>.toKotlinResult(): Result<T> {
  return when (this) {
    is OkResult -> Result.success(this.result)
    is ErrorResult -> Result.failure(this.exception)
  }
}