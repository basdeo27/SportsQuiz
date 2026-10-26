package com.elliotmoose.Sports.Quiz.daily

class DailyNotFoundException(message: String) : RuntimeException(message)
class DailySessionNotFoundException(message: String) : RuntimeException(message)
class DailySessionCompletedException(message: String) : RuntimeException(message)
class NoMoreHintsException(message: String) : RuntimeException(message)
class InvalidDailyRequestException(message: String) : RuntimeException(message)
class DailyScoreConflictException(message: String) : RuntimeException(message)
