package com.elliotmoose.Sports.Quiz.quiz

class QuizNotFoundException(quizId: String) : RuntimeException("Quiz not found: $quizId")
class QuestionNotFoundException(questionId: String) : RuntimeException("Question not found: $questionId")
class InvalidQuizRequestException(message: String) : RuntimeException(message)
