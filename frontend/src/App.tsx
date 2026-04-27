import { useEffect, useMemo, useRef, useState, useCallback } from "react";
import logo from "./assets/sports-quiz-logo.png";

type League = "NBA" | "MLB" | "NFL" | "NHL" | "EPL";
type QuizType = "LOGO" | "FACE";
type QuizDifficulty = "EASY" | "MEDIUM" | "HARD";
type FaceQuizMode = "LEAGUE" | "TEAM";

type QuizQuestion = {
  id: string;
  league: League;
  logoUrl: string;
  fullName: string;
  hints?: Partial<Record<QuizDifficulty, string[]>> | null;
};

type QuizResponse = {
  quizId: string;
  questions: QuizQuestion[];
};

type FaceTeamOption = {
  teamId: string;
  teamName: string;
  league: League;
  playerCount: number;
};

type QuizReviewQuestion = {
  id: string;
  league: League;
  logoUrl: string;
  fullName: string;
  submittedAnswer: string | null;
  correct: boolean | null;
  skipped: boolean | null;
  hinted: boolean;
};

type QuizReviewResponse = {
  quizId: string;
  difficulty: string;
  questions: QuizReviewQuestion[];
  totalQuestions: number;
  correctCount: number;
  incorrectCount: number;
  skippedCount: number;
  elapsedSeconds: number;
  score: number;
};

const API_ROOT = import.meta.env.VITE_API_BASE_URL ?? "";
const API_BASE_URL = `${API_ROOT.replace(/\/$/, "")}/v0`;

const quizTypeOptions = [
  {
    value: "LOGO",
    label: "Logos",
    description: "Guess the team from its badge or crest."
  },
  {
    value: "FACE",
    label: "Faces",
    description: "Guess the player from a headshot. Easy and medium use all-stars only."
  }
] as const;
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

type Screen =
  | "home"
  | "login"
  | "register"
  | "mode"
  | "faceScope"
  | "setup"
  | "quiz"
  | "complete"
  | "review";

type AccountResponse = {
  id: string;
  username: string;
  nickname: string;
  createdAtMillis: number;
};

type QuizHistoryItem = {
  quizId: string;
  userId: string;
  quizType: QuizType;
  difficulty: QuizDifficulty;
  leagues: League[];
  score: number;
  correctCount: number;
  totalQuestions: number;
  completedAtMillis: number;
};

type QuizHistoryResponse = {
  results: QuizHistoryItem[];
  hasMore: boolean;
};

const MIN_QUESTIONS = 1;
const MAX_QUESTIONS = 25;

export default function App() {
  const [screen, setScreen] = useState<Screen>("home");
  const [quizType, setQuizType] = useState<QuizType>("LOGO");
  const [faceQuizMode, setFaceQuizMode] = useState<FaceQuizMode>("LEAGUE");
  const [numberOfQuestions, setNumberOfQuestions] = useState(10);
  const [availableLeaguesByType, setAvailableLeaguesByType] = useState<Record<QuizType, League[]>>({ LOGO: [], FACE: [] });
  const [selectedLeagues, setSelectedLeagues] = useState<Set<League>>(new Set());
  const [faceTeamOptions, setFaceTeamOptions] = useState<FaceTeamOption[]>([]);
  const [selectedFaceTeamIds, setSelectedFaceTeamIds] = useState<Set<string>>(
    new Set()
  );
  const [teamSearch, setTeamSearch] = useState("");
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
  const [hintedCount, setHintedCount] = useState(0);
  const [attemptsByQuestion, setAttemptsByQuestion] = useState<
    Record<string, number>
  >({});
  const [reviewData, setReviewData] = useState<QuizReviewResponse | null>(null);
  const [aiSummary, setAiSummary] = useState<string | null>(null);
  const [aiSummaryLoading, setAiSummaryLoading] = useState(false);
  const [hintsByQuestion, setHintsByQuestion] = useState<Record<string, string>>(
    {}
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [currentUser, setCurrentUser] = useState<AccountResponse | null>(null);
  const [authUsername, setAuthUsername] = useState("");
  const [authPassword, setAuthPassword] = useState("");
  const [authNickname, setAuthNickname] = useState("");
  const [accountPanelOpen, setAccountPanelOpen] = useState(false);
  const [accountHistory, setAccountHistory] = useState<QuizHistoryItem[]>([]);
  const [accountHistoryLoading, setAccountHistoryLoading] = useState(false);
  const [accountHistoryHasMore, setAccountHistoryHasMore] = useState(false);
  const accountPanelRef = useRef<HTMLDivElement | null>(null);
  const answerInputRef = useRef<HTMLInputElement | null>(null);

  const currentQuestion = useMemo(
    () => questions[currentIndex],
    [questions, currentIndex]
  );

  useEffect(() => {
    fetch(`${API_BASE_URL}/quiz/leagues`)
      .then((r) => r.json())
      .then((data: Record<QuizType, League[]>) => {
        setAvailableLeaguesByType(data);
        setSelectedLeagues(new Set(data.LOGO));
      });
  }, []);

  useEffect(() => {
    if (screen === "quiz" && answerInputRef.current) {
      answerInputRef.current.focus();
    }
  }, [screen, currentIndex]);

  useEffect(() => {
    if (!accountPanelOpen || !currentUser) return;
    setAccountHistory([]);
    setAccountHistoryHasMore(false);
    let cancelled = false;
    setAccountHistoryLoading(true);
    fetch(`${API_BASE_URL}/quiz/results?userId=${currentUser.id}&limit=10`)
      .then((r) => (r.ok ? r.json() : null))
      .then((data: QuizHistoryResponse | null) => {
        if (cancelled || !data) return;
        setAccountHistory(data.results);
        setAccountHistoryHasMore(data.hasMore);
      })
      .catch(() => {})
      .finally(() => { if (!cancelled) setAccountHistoryLoading(false); });
    return () => { cancelled = true; };
  }, [accountPanelOpen, currentUser]);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (accountPanelRef.current && !accountPanelRef.current.contains(event.target as Node)) {
        setAccountPanelOpen(false);
      }
    };
    if (accountPanelOpen) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [accountPanelOpen]);

  const loadMoreHistory = useCallback(async () => {
    if (!currentUser || accountHistoryLoading || accountHistory.length === 0) return;
    const before = accountHistory[accountHistory.length - 1].completedAtMillis;
    setAccountHistoryLoading(true);
    try {
      const response = await fetch(
        `${API_BASE_URL}/quiz/results?userId=${currentUser.id}&limit=10&before=${before}`
      );
      if (!response.ok) return;
      const data = (await response.json()) as QuizHistoryResponse;
      setAccountHistory((prev) => [...prev, ...data.results]);
      setAccountHistoryHasMore(data.hasMore);
    } catch {
      // silent
    } finally {
      setAccountHistoryLoading(false);
    }
  }, [currentUser, accountHistoryLoading, accountHistory]);

  const formatDate = (millis: number) =>
    new Date(millis).toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });

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
    setAiSummary(null);
    setAiSummaryLoading(false);
    setHintsByQuestion({});
    setHintedCount(0);
    setDifficulty("EASY");
    setQuizType("LOGO");
    setFaceQuizMode("LEAGUE");
    setSelectedLeagues(new Set(availableLeaguesByType.LOGO));
    setSelectedFaceTeamIds(new Set());
    setTeamSearch("");
    setError(null);
  };

  const goHome = () => {
    resetQuiz();
    setScreen("home");
  };

  const clearAuthFields = () => {
    setAuthUsername("");
    setAuthPassword("");
    setAuthNickname("");
    setError(null);
  };

  const login = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${API_BASE_URL}/account/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: authUsername, password: authPassword })
      });
      if (!response.ok) {
        const payload = (await response.json()) as { message?: string };
        throw new Error(payload.message || "Login failed.");
      }
      const user = (await response.json()) as AccountResponse;
      setCurrentUser(user);
      clearAuthFields();
      setScreen("home");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error.");
    } finally {
      setLoading(false);
    }
  };

  const register = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${API_BASE_URL}/account`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: authUsername, password: authPassword, nickname: authNickname })
      });
      if (!response.ok) {
        const payload = (await response.json()) as { message?: string };
        throw new Error(payload.message || "Registration failed.");
      }
      const user = (await response.json()) as AccountResponse;
      setCurrentUser(user);
      clearAuthFields();
      setScreen("home");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error.");
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    setCurrentUser(null);
    setAccountPanelOpen(false);
    setAccountHistory([]);
    setAccountHistoryHasMore(false);
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

  const toggleFaceTeam = (teamId: string) => {
    setSelectedFaceTeamIds((prev) => {
      const next = new Set(prev);
      if (next.has(teamId)) {
        next.delete(teamId);
      } else {
        next.add(teamId);
      }
      return next;
    });
  };

  const loadFaceTeams = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${API_BASE_URL}/quiz/teams`);
      if (!response.ok) {
        const payload = (await response.json()) as { message?: string };
        throw new Error(payload.message || "Failed to load face teams.");
      }
      const payload = (await response.json()) as FaceTeamOption[];
      setFaceTeamOptions(payload);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error.");
    } finally {
      setLoading(false);
    }
  };

  const chooseFaceQuizMode = async (mode: FaceQuizMode) => {
    setFaceQuizMode(mode);
    setError(null);
    if (mode === "TEAM" && faceTeamOptions.length === 0) {
      await loadFaceTeams();
    }
    setScreen("setup");
  };

  const startQuiz = async () => {
    const selectedTeams = faceTeamOptions.filter((team) =>
      selectedFaceTeamIds.has(team.teamId)
    );
    const leaguesForRequest =
      quizType === "FACE" && faceQuizMode === "TEAM"
        ? Array.from(new Set(selectedTeams.map((team) => team.league)))
        : Array.from(selectedLeagues);

    if (quizType === "FACE" && faceQuizMode === "TEAM") {
      if (selectedFaceTeamIds.size === 0) {
        setError("Pick at least one team to begin.");
        return;
      }
    } else if (selectedLeagues.size === 0) {
      setError("Pick at least one league to begin.");
      return;
    }

    setLoading(true);
    setError(null);
    setFeedback(null);
    setFeedbackStatus(null);
    try {
      const response = await fetch(`${API_BASE_URL}/quiz`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
          leagues: leaguesForRequest,
          numberOfQuestions,
          difficulty,
          type: quizType,
          teamIds:
            quizType === "FACE" && faceQuizMode === "TEAM"
              ? Array.from(selectedFaceTeamIds)
              : [],
          ...(currentUser ? { accountId: currentUser.id } : {})
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
      setHintsByQuestion({});
      setHintedCount(0);
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
      const response = await fetch(`${API_BASE_URL}/quiz/answer`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          quizId,
          questionId: currentQuestion.id,
          answer: submittedAnswer,
          hintUsed: Boolean(hintsByQuestion[currentQuestion.id])
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
            fetchReviewData(false);
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
      const response = await fetch(`${API_BASE_URL}/quiz/skip`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          quizId,
          questionId: currentQuestion.id,
          hintUsed: Boolean(hintsByQuestion[currentQuestion.id])
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
          fetchReviewData(false);
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

  const requestHint = () => {
    if (!currentQuestion || loading) {
      return;
    }
    if (hintsByQuestion[currentQuestion.id]) {
      return;
    }
    const hintText =
      currentQuestion.hints?.[difficulty]?.find((hint) => hint.trim().length > 0) ??
      "No hint available.";
    setHintsByQuestion((prev) => ({
      ...prev,
      [currentQuestion.id]: hintText
    }));
    setHintedCount((prev) => prev + 1);
  };

  const fetchAiSummary = async (id: string) => {
    setAiSummaryLoading(true);
    setAiSummary(null);
    try {
      const response = await fetch(`${API_BASE_URL}/ai/quiz-summary`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ quizId: id }),
      });
      if (!response.ok) return;
      const payload = (await response.json()) as { summary: string };
      setAiSummary(payload.summary);
    } catch {
      // silent — AI summary is non-critical
    } finally {
      setAiSummaryLoading(false);
    }
  };

  const fetchReviewData = async (showReview: boolean) => {
    if (!quizId) {
      return;
    }
    setLoading(true);
    setError(null);
    fetchAiSummary(quizId);
    try {
      const response = await fetch(`${API_BASE_URL}/quiz/${quizId}`);
      if (!response.ok) {
        const payload = (await response.json()) as { message?: string };
        throw new Error(payload.message || "Failed to load quiz review.");
      }
      const payload = (await response.json()) as QuizReviewResponse;
      setReviewData(payload);
      if (showReview) {
        setScreen("review");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error.");
    } finally {
      setLoading(false);
    }
  };

  const quizTypeLabel = quizType === "LOGO" ? "Logos" : "Faces";
  const quizImageAlt = quizType === "LOGO" ? "team logo" : "player headshot";
  const filteredFaceTeams = useMemo(() => {
    const query = teamSearch.trim().toLowerCase();
    if (!query) {
      return faceTeamOptions;
    }
    return faceTeamOptions.filter((team) => {
      return (
        team.teamName.toLowerCase().includes(query) ||
        team.league.toLowerCase().includes(query)
      );
    });
  }, [faceTeamOptions, teamSearch]);

  return (
    <div className="app">
      <header className="app__header">
        <div className="brand">Sports Quiz</div>
        <div className="app__header-actions">
          {screen !== "home" && screen !== "login" && screen !== "register" && (
            <button className="ghost-button" onClick={goHome}>
              Exit Quiz
            </button>
          )}
          {currentUser ? (
            <div className="account-menu" ref={accountPanelRef}>
              <button
                className="ghost-button account-menu__trigger"
                onClick={() => setAccountPanelOpen((prev) => !prev)}
              >
                {currentUser.nickname}
              </button>
              {accountPanelOpen && (
                <div className="account-panel">
                  <div className="account-panel__header">
                    <span className="account-panel__nickname">{currentUser.nickname}</span>
                  </div>
                  <div className="account-panel__history">
                    <p className="account-panel__section-label">Recent Quizzes</p>
                    {accountHistoryLoading && accountHistory.length === 0 ? (
                      <p className="account-panel__empty">Loading...</p>
                    ) : accountHistory.length === 0 ? (
                      <p className="account-panel__empty">No quizzes yet.</p>
                    ) : (
                      <>
                        {accountHistory.map((item) => (
                          <div key={item.quizId} className="account-panel__quiz-item">
                            <div className="account-panel__quiz-top">
                              <span className="tag">{item.quizType === "LOGO" ? "Logos" : "Faces"}</span>
                              <span className="account-panel__score">{item.score} pts</span>
                            </div>
                            <div className="account-panel__quiz-date">{formatDate(item.completedAtMillis)}</div>
                          </div>
                        ))}
                        {accountHistoryHasMore && (
                          <button
                            className="ghost-button account-panel__load-more"
                            onClick={loadMoreHistory}
                            disabled={accountHistoryLoading}
                          >
                            {accountHistoryLoading ? "Loading..." : "Load more"}
                          </button>
                        )}
                      </>
                    )}
                  </div>
                  <div className="account-panel__footer">
                    <button className="ghost-button account-panel__logout" onClick={logout}>
                      Log out
                    </button>
                  </div>
                </div>
              )}
            </div>
          ) : (
            screen === "home" && (
              <button className="ghost-button" onClick={() => { clearAuthFields(); setScreen("login"); }}>
                Login
              </button>
            )
          )}
        </div>
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
            <button className="primary-button" onClick={() => setScreen("mode")}>
              New Quiz
            </button>
          </section>
        )}

        {screen === "login" && (
          <section className="card hero">
            <p className="eyebrow">Welcome back</p>
            <h1>Login</h1>
            <form
              className="quiz__form"
              onSubmit={(e) => { e.preventDefault(); login(); }}
            >
              <input
                type="text"
                placeholder="Username"
                value={authUsername}
                onChange={(e) => setAuthUsername(e.target.value)}
                disabled={loading}
                autoFocus
              />
              <input
                type="password"
                placeholder="Password"
                value={authPassword}
                onChange={(e) => setAuthPassword(e.target.value)}
                disabled={loading}
              />
              {error && <div className="message error">{error}</div>}
              <div className="quiz__actions">
                <button className="primary-button" type="submit" disabled={loading}>
                  {loading ? "Logging in..." : "Login"}
                </button>
                <button
                  className="ghost-button"
                  type="button"
                  onClick={() => { clearAuthFields(); setScreen("register"); }}
                >
                  Create account
                </button>
                <button className="ghost-button" type="button" onClick={goHome}>
                  Back
                </button>
              </div>
            </form>
          </section>
        )}

        {screen === "register" && (
          <section className="card hero">
            <p className="eyebrow">Join the quiz</p>
            <h1>Create account</h1>
            <form
              className="quiz__form"
              onSubmit={(e) => { e.preventDefault(); register(); }}
            >
              <input
                type="text"
                placeholder="Username"
                value={authUsername}
                onChange={(e) => setAuthUsername(e.target.value)}
                disabled={loading}
                autoFocus
              />
              <input
                type="password"
                placeholder="Password (min. 8 characters)"
                value={authPassword}
                onChange={(e) => setAuthPassword(e.target.value)}
                disabled={loading}
              />
              <input
                type="text"
                placeholder="Nickname (your display name)"
                value={authNickname}
                onChange={(e) => setAuthNickname(e.target.value)}
                disabled={loading}
              />
              {error && <div className="message error">{error}</div>}
              <div className="quiz__actions">
                <button className="primary-button" type="submit" disabled={loading}>
                  {loading ? "Creating account..." : "Create account"}
                </button>
                <button
                  className="ghost-button"
                  type="button"
                  onClick={() => { clearAuthFields(); setScreen("login"); }}
                >
                  Already have an account?
                </button>
                <button className="ghost-button" type="button" onClick={goHome}>
                  Back
                </button>
              </div>
            </form>
          </section>
        )}

        {screen === "mode" && (
          <section className="card setup">
            <h2>Choose your quiz</h2>
            <p className="supporting">
              Start with the question style, then narrow down the leagues.
            </p>
            <div className="quiz-type-grid">
              {quizTypeOptions.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  className={`quiz-type-card ${
                    quizType === option.value ? "is-selected" : ""
                  }`}
                  onClick={() => {
                    setQuizType(option.value);
                    setSelectedLeagues(new Set(option.value === "FACE" ? availableLeaguesByType.FACE : availableLeaguesByType.LOGO));
                    if (option.value === "FACE") {
                      setScreen("faceScope");
                    } else {
                      setScreen("setup");
                    }
                    setError(null);
                  }}
                >
                  <div className="quiz-type-card__title">{option.label}</div>
                  <div className="quiz-type-card__description">
                    {option.description}
                  </div>
                </button>
              ))}
            </div>
            <div className="setup__actions">
              <button className="ghost-button" onClick={goHome}>
                Back
              </button>
            </div>
          </section>
        )}

        {screen === "faceScope" && (
          <section className="card setup">
            <h2>Choose your face quiz</h2>
            <p className="supporting">
              Pick whether to quiz by league or narrow the faces down to one or more
              specific teams.
            </p>
            <div className="quiz-type-grid">
              <button
                type="button"
                className={`quiz-type-card ${
                  faceQuizMode === "LEAGUE" ? "is-selected" : ""
                }`}
                onClick={() => chooseFaceQuizMode("LEAGUE")}
              >
                <div className="quiz-type-card__title">By League</div>
                <div className="quiz-type-card__description">
                  Select one or more leagues. Easy and medium stay all-star only.
                </div>
              </button>
              <button
                type="button"
                className={`quiz-type-card ${
                  faceQuizMode === "TEAM" ? "is-selected" : ""
                }`}
                onClick={() => chooseFaceQuizMode("TEAM")}
              >
                <div className="quiz-type-card__title">By Team</div>
                <div className="quiz-type-card__description">
                  Select one or more teams. Any player from those teams can appear.
                </div>
              </button>
            </div>
            {error && <div className="message error">{error}</div>}
            <div className="setup__actions">
              <button className="ghost-button" onClick={() => setScreen("mode")}>
                Back
              </button>
            </div>
          </section>
        )}

        {screen === "setup" && (
          <section className="card setup">
            <h2>Build your {quizType === "LOGO" ? "logo" : "face"} quiz</h2>
            <p className="supporting">
              {quizType === "LOGO"
                ? "Pick the leagues, question count, and difficulty."
                : faceQuizMode === "TEAM"
                  ? "Pick the teams, question count, and difficulty. Team face quizzes can include any player on the selected teams."
                  : "Pick the leagues, question count, and difficulty. Easy and medium face quizzes are limited to all-stars."}
            </p>
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
              {quizType === "FACE" && faceQuizMode === "TEAM" ? (
                <>
                  <div className="setup__split">
                    <p>Select teams</p>
                    <span className="tag">
                      {selectedFaceTeamIds.size} selected
                    </span>
                  </div>
                  <input
                    className="setup__search"
                    type="text"
                    placeholder="Search teams or leagues..."
                    value={teamSearch}
                    onChange={(event) => setTeamSearch(event.target.value)}
                    disabled={loading}
                  />
                  <div className="team-grid">
                    {filteredFaceTeams.map((team) => (
                      <button
                        key={team.teamId}
                        type="button"
                        className={`team-card ${
                          selectedFaceTeamIds.has(team.teamId) ? "is-selected" : ""
                        }`}
                        onClick={() => toggleFaceTeam(team.teamId)}
                      >
                        <div className="team-card__title">{team.teamName}</div>
                        <div className="team-card__meta">
                          {team.league} · {team.playerCount} players
                        </div>
                      </button>
                    ))}
                  </div>
                  {!loading && filteredFaceTeams.length === 0 && (
                    <div className="message error">
                      No teams match that search.
                    </div>
                  )}
                </>
              ) : (
                <>
                  <p>Select leagues</p>
                  <div className="league-grid">
                    {(quizType === "FACE" ? availableLeaguesByType.FACE : availableLeaguesByType.LOGO).map((league) => (
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
                </>
              )}
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

            <div className="setup__row">
              <p>Quiz type</p>
              <div className="quiz-type-inline">
                <span className="tag">{quizTypeLabel}</span>
                <button
                  type="button"
                  className="ghost-button"
                  onClick={() =>
                    setScreen(quizType === "FACE" ? "faceScope" : "mode")
                  }
                >
                  Change
                </button>
              </div>
            </div>

            {error && <div className="message error">{error}</div>}

            <div className="setup__actions">
              <button
                className="ghost-button"
                onClick={() => setScreen(quizType === "FACE" ? "faceScope" : "mode")}
              >
                Back
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
              <span className="tag">
                {currentQuestion.league} · {quizTypeLabel}
              </span>
            </div>
            <div className="logo-frame">
              <img
                src={currentQuestion.logoUrl}
                alt={`${currentQuestion.league} ${quizImageAlt}`}
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
                placeholder={
                  quizType === "LOGO"
                    ? "Type the team name..."
                    : "Type the player name..."
                }
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
                <button
                  className="ghost-button"
                  type="button"
                  onClick={requestHint}
                  disabled={loading || Boolean(hintsByQuestion[currentQuestion.id])}
                >
                  {hintsByQuestion[currentQuestion.id] ? "Hint used" : "Hint"}
                </button>
              </div>
            </form>
            {hintsByQuestion[currentQuestion.id] && (
              <div className="message hint">
                {hintsByQuestion[currentQuestion.id]}
              </div>
            )}
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
              {skippedCount} skipped. {hintedCount} hint
              {hintedCount === 1 ? "" : "s"} used.
            </p>
            <div className="score-summary">
              <span className="score-summary__label">Score</span>
              <span className="score-summary__value">
                {reviewData ? reviewData.score : "Calculating..."}
              </span>
            </div>
            <p className="supporting">
              Want another run? Change the leagues or go again.
            </p>
            <div className="stack">
              <button className="primary-button" onClick={() => fetchReviewData(true)}>
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
            {reviewData && (
              <div className="review__summary">
                <div className="review__stat">
                  <span className="review__label">Score</span>
                  <span className="review__value">{reviewData.score}</span>
                </div>
                <div className="review__stat">
                  <span className="review__label">Accuracy</span>
                  <span className="review__value">
                    {Math.round(
                      (reviewData.correctCount / reviewData.totalQuestions) * 100
                    )}
                    %
                  </span>
                </div>
                <div className="review__stat">
                  <span className="review__label">Time</span>
                  <span className="review__value">{reviewData.elapsedSeconds}s</span>
                </div>
              </div>
            )}
            <div className="ai-summary">
              {aiSummaryLoading ? (
                <div className="ai-summary__loading">
                  <span className="ai-summary__dot" />
                  <span className="ai-summary__dot" />
                  <span className="ai-summary__dot" />
                </div>
              ) : aiSummary ? (
                <p className="ai-summary__text">{aiSummary}</p>
              ) : null}
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
                      <img src={result.logoUrl} alt={`${result.league} ${quizImageAlt}`} />
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
                        <span className="review__label">Hint used</span>
                        <span className="review__value">
                          {result.hinted ? "Yes" : "No"}
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
