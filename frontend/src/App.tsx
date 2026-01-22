import { useEffect, useMemo, useRef, useState } from "react";
import logo from "./assets/sports-quiz-logo.png";

type League = "NBA" | "MLB" | "NFL" | "NHL";

type QuizQuestion = {
  id: string;
  league: League;
  logoUrl: string;
  fullName: string;
};

type QuizResponse = {
  quizId: string;
  questions: QuizQuestion[];
};

type QuizReviewQuestion = {
  id: string;
  league: League;
  logoUrl: string;
  fullName: string;
  submittedAnswer: string | null;
  correct: boolean | null;
  skipped: boolean | null;
};

type QuizReviewResponse = {
  quizId: string;
  difficulty: string;
  questions: QuizReviewQuestion[];
};

const allLeagues: League[] = ["NBA", "MLB", "NFL", "NHL"];
const difficultyOptions = [
  {
    value: "EASY",
    label: "Easy",
    description: "Typo-friendly. Most of the name is enough."
  },
  {
    value: "MEDIUM",
    label: "Medium",
    description: "Must match exactly one of the accepted names."
  },
  {
    value: "HARD",
    label: "Hard",
    description: "City + team name required, no shortcuts."
  }
] as const;

type Screen = "home" | "setup" | "quiz" | "complete" | "review";

const MIN_QUESTIONS = 10;
const MAX_QUESTIONS = 25;

export default function App() {
  const [screen, setScreen] = useState<Screen>("home");
  const [numberOfQuestions, setNumberOfQuestions] = useState(10);
  const [selectedLeagues, setSelectedLeagues] = useState<Set<League>>(
    new Set(allLeagues)
  );
  const [difficulty, setDifficulty] =
    useState<(typeof difficultyOptions)[number]["value"]>("EASY");
  const [quizId, setQuizId] = useState("");
  const [questions, setQuestions] = useState<QuizQuestion[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answer, setAnswer] = useState("");
  const [feedback, setFeedback] = useState<string | null>(null);
  const [feedbackStatus, setFeedbackStatus] = useState<"success" | "error" | null>(
    null
  );
  const [correctCount, setCorrectCount] = useState(0);
  const [incorrectCount, setIncorrectCount] = useState(0);
  const [skippedCount, setSkippedCount] = useState(0);
  const [attemptsByQuestion, setAttemptsByQuestion] = useState<
    Record<string, number>
  >({});
  const [reviewData, setReviewData] = useState<QuizReviewResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const answerInputRef = useRef<HTMLInputElement | null>(null);

  const currentQuestion = useMemo(
    () => questions[currentIndex],
    [questions, currentIndex]
  );

  useEffect(() => {
    if (screen === "quiz" && answerInputRef.current) {
      answerInputRef.current.focus();
    }
  }, [screen, currentIndex]);

  const resetQuiz = () => {
    setQuizId("");
    setQuestions([]);
    setCurrentIndex(0);
    setAnswer("");
    setFeedback(null);
    setFeedbackStatus(null);
    setCorrectCount(0);
    setIncorrectCount(0);
    setSkippedCount(0);
    setAttemptsByQuestion({});
    setReviewData(null);
    setDifficulty("EASY");
    setError(null);
  };

  const goHome = () => {
    resetQuiz();
    setScreen("home");
  };

  const toggleLeague = (league: League) => {
    setSelectedLeagues((prev) => {
      const next = new Set(prev);
      if (next.has(league)) {
        next.delete(league);
      } else {
        next.add(league);
      }
      return next;
    });
  };

  const startQuiz = async () => {
    if (selectedLeagues.size === 0) {
      setError("Pick at least one league to begin.");
      return;
    }

    setLoading(true);
    setError(null);
    setFeedback(null);
    setFeedbackStatus(null);
    try {
      const response = await fetch("/v0/quiz", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          leagues: Array.from(selectedLeagues),
          numberOfQuestions,
          difficulty
        })
      });

      if (!response.ok) {
        const payload = (await response.json()) as { message?: string };
        throw new Error(payload.message || "Failed to create quiz.");
      }

      const payload = (await response.json()) as QuizResponse;
      setQuizId(payload.quizId);
      setQuestions(payload.questions);
      setCurrentIndex(0);
      setAnswer("");
      setCorrectCount(0);
      setIncorrectCount(0);
      setSkippedCount(0);
      setAttemptsByQuestion({});
      setReviewData(null);
      setScreen("quiz");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error.");
    } finally {
      setLoading(false);
    }
  };

  const submitAnswer = async () => {
    if (!currentQuestion || loading) {
      return;
    }
    const submittedAnswer = answer.trim();
    if (!submittedAnswer) {
      setError("Enter a team name before submitting.");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const response = await fetch("/v0/quiz/answer", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          quizId,
          questionId: currentQuestion.id,
          answer: submittedAnswer
        })
      });

      if (!response.ok) {
        const payload = (await response.json()) as { message?: string };
        throw new Error(payload.message || "Failed to submit answer.");
      }

      const payload = (await response.json()) as {
        correct: boolean;
        correctAnswer: string;
        attemptsRemaining?: number;
        shouldAdvance?: boolean;
      };
      const newAttempts = (attemptsByQuestion[currentQuestion.id] ?? 0) + 1;
      setAttemptsByQuestion((prev) => ({
        ...prev,
        [currentQuestion.id]: newAttempts
      }));

      const shouldAdvance =
        payload.shouldAdvance ??
        (difficulty === "EASY" ? payload.correct || newAttempts >= 2 : true);
      const attemptsRemaining =
        payload.attemptsRemaining ??
        (difficulty === "EASY" ? Math.max(0, 2 - newAttempts) : 0);
      if (payload.correct) {
        setCorrectCount((prev) => prev + 1);
        setFeedbackStatus("success");
        setFeedback("Correct!");
      } else {
        setFeedbackStatus("error");
        setFeedback(
          attemptsRemaining === 1 ? "Not quite. Last chance!" : "Not quite."
        );
      }

      setAnswer("");

      if (shouldAdvance) {
        if (!payload.correct) {
          setIncorrectCount((prev) => prev + 1);
        }

        window.setTimeout(() => {
          setFeedback(null);
          setFeedbackStatus(null);
          const nextIndex = currentIndex + 1;
          if (nextIndex >= questions.length) {
            setScreen("complete");
          } else {
            setCurrentIndex(nextIndex);
          }
        }, 650);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error.");
    } finally {
      setLoading(false);
    }
  };

  const skipQuestion = async () => {
    if (!currentQuestion || loading) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await fetch("/v0/quiz/skip", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          quizId,
          questionId: currentQuestion.id
        })
      });

      if (!response.ok) {
        const payload = (await response.json()) as { message?: string };
        throw new Error(payload.message || "Failed to skip question.");
      }

      setSkippedCount((prev) => prev + 1);
      setFeedbackStatus("error");
      setFeedback("Skipped.");

      window.setTimeout(() => {
        setFeedback(null);
        setFeedbackStatus(null);
        const nextIndex = currentIndex + 1;
        if (nextIndex >= questions.length) {
          setScreen("complete");
        } else {
          setCurrentIndex(nextIndex);
        }
      }, 400);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error.");
    } finally {
      setLoading(false);
    }
  };

  const loadReview = async () => {
    if (!quizId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`/v0/quiz/${quizId}`);
      if (!response.ok) {
        const payload = (await response.json()) as { message?: string };
        throw new Error(payload.message || "Failed to load quiz review.");
      }
      const payload = (await response.json()) as QuizReviewResponse;
      setReviewData(payload);
      setScreen("review");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app">
      <header className="app__header">
        <div className="brand">Sports Quiz</div>
        {screen !== "home" && (
          <button className="ghost-button" onClick={goHome}>
            Exit Quiz
          </button>
        )}
      </header>

      <main className="app__main">
        {screen === "home" && (
          <section className="card hero">
            <div className="hero__logo">
              <img src={logo} alt="Sports Quiz logo" />
            </div>
            <p className="eyebrow">Welcome to the sports quiz app</p>
            <p className="supporting">
              Choose your leagues, set your length, and race through the logos.
            </p>
            <button className="primary-button" onClick={() => setScreen("setup")}>
              New Quiz
            </button>
          </section>
        )}

        {screen === "setup" && (
          <section className="card setup">
            <h2>Build your quiz</h2>
            <div className="setup__row">
              <label htmlFor="questionCount">Number of questions</label>
              <div className="setup__count">
                <input
                  id="questionCount"
                  type="range"
                  min={MIN_QUESTIONS}
                  max={MAX_QUESTIONS}
                  value={numberOfQuestions}
                  onChange={(event) =>
                    setNumberOfQuestions(Number(event.target.value))
                  }
                />
                <span>{numberOfQuestions}</span>
              </div>
            </div>

            <div className="setup__row">
              <p>Select leagues</p>
              <div className="league-grid">
                {allLeagues.map((league) => (
                  <label key={league} className="league-chip">
                    <input
                      type="checkbox"
                      checked={selectedLeagues.has(league)}
                      onChange={() => toggleLeague(league)}
                    />
                    <span>{league}</span>
                  </label>
                ))}
              </div>
            </div>

            <div className="setup__row">
              <p>Choose difficulty</p>
              <div className="difficulty-grid">
                {difficultyOptions.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    className={`difficulty-card ${
                      difficulty === option.value ? "is-selected" : ""
                    }`}
                    onClick={() => setDifficulty(option.value)}
                  >
                    <div className="difficulty-card__title">
                      {option.label}
                    </div>
                    <div className="difficulty-card__description">
                      {option.description}
                    </div>
                  </button>
                ))}
              </div>
            </div>

            {error && <div className="message error">{error}</div>}

            <div className="setup__actions">
              <button className="ghost-button" onClick={goHome}>
                Cancel
              </button>
              <button
                className="primary-button"
                onClick={startQuiz}
                disabled={loading}
              >
                {loading ? "Starting..." : "Start Quiz"}
              </button>
            </div>
          </section>
        )}

        {screen === "quiz" && currentQuestion && (
          <section className="card quiz">
            <div className="quiz__meta">
              <span>
                Question {currentIndex + 1} of {questions.length}
              </span>
              <span className="tag">{currentQuestion.league}</span>
            </div>
            <div className="logo-frame">
              <img
                src={currentQuestion.logoUrl}
                alt={`${currentQuestion.league} team logo`}
              />
            </div>
            <form
              className="quiz__form"
              onSubmit={(event) => {
                event.preventDefault();
                submitAnswer();
              }}
            >
              <input
                type="text"
                placeholder="Type the team name..."
                value={answer}
                onChange={(event) => setAnswer(event.target.value)}
                disabled={loading}
                ref={answerInputRef}
              />
              <div className="quiz__actions">
                <button className="primary-button" type="submit" disabled={loading}>
                  {loading ? "Checking..." : "Submit"}
                </button>
                <button
                  className="ghost-button"
                  type="button"
                  onClick={skipQuestion}
                  disabled={loading}
                >
                  Skip
                </button>
              </div>
            </form>
            {feedback && (
              <div className={`message ${feedbackStatus ?? "success"}`}>
                {feedback}
              </div>
            )}
            {error && <div className="message error">{error}</div>}
          </section>
        )}

        {screen === "complete" && (
          <section className="card hero">
            <p className="eyebrow">All done</p>
            <h1>Quiz complete</h1>
            <p className="supporting">
              You answered {correctCount} right, {incorrectCount} wrong, and{" "}
              {skippedCount} skipped.
            </p>
            <p className="supporting">
              Want another run? Change the leagues or go again.
            </p>
            <div className="stack">
              <button className="primary-button" onClick={loadReview}>
                Review Results
              </button>
              <button className="primary-button" onClick={() => setScreen("setup")}>
                New Quiz
              </button>
              <button className="ghost-button" onClick={goHome}>
                Back Home
              </button>
            </div>
          </section>
        )}

        {screen === "review" && (
          <section className="card review">
            <div className="review__header">
              <h2>Quiz Results</h2>
              <button className="ghost-button" onClick={goHome}>
                Back Home
              </button>
            </div>
            <div className="review__list">
              {reviewData?.questions.map((result, index) => (
                <article key={result.id} className="review__item">
                  <div className="review__meta">
                    <span>Question {index + 1}</span>
                    <span className="tag">{result.league}</span>
                  </div>
                  <div className="review__content">
                    <div className="review__logo">
                      <img src={result.logoUrl} alt={`${result.league} team logo`} />
                    </div>
                    <div className="review__answers">
                      <div className="review__row">
                        <span className="review__label">Your answer</span>
                        <span
                          className={`review__value ${
                            result.skipped
                              ? "is-skipped"
                              : result.correct
                              ? "is-correct"
                              : "is-wrong"
                          }`}
                        >
                          {result.skipped
                            ? "Skipped"
                            : result.submittedAnswer || "No answer"}
                        </span>
                      </div>
                      <div className="review__row">
                        <span className="review__label">Correct answer</span>
                        <span className="review__value">
                          {result.fullName}
                        </span>
                      </div>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          </section>
        )}
      </main>
    </div>
  );
}
