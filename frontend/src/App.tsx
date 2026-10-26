import { useEffect, useMemo, useRef, useState, useCallback } from "react";
import { apiFetch } from "./api";
import { ResultShare } from "./ResultShare";
import logo from "./assets/sports-quiz-logo.png";
import { DailyScores, type DailyScore } from "./DailyScores";
import { DailyAccomplishments, type PlayerAccomplishments } from "./DailyAccomplishments";

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

type DailyHintType = "IMAGE" | "AUDIO" | "VIDEO" | "TEXT" | "STAT_LINE" | "ACCOMPLISHMENTS";
type DailyHintSource = "REMOTE" | "LOCAL";

type DailyStat = { name: string; value: string };
type DailyStatLine = { label: string; stats: DailyStat[] };

type DailyHint =
  | { type: "IMAGE"; url: string; source: DailyHintSource }
  | { type: "AUDIO"; url: string; source: DailyHintSource }
  | { type: "VIDEO"; url: string; source: DailyHintSource }
  | { type: "TEXT"; value: string; attribution?: string | null }
  | { type: "STAT_LINE"; statLine: DailyStatLine }
  | { type: "ACCOMPLISHMENTS"; accomplishments: PlayerAccomplishments };

function resolveHintUrl(hint: { url: string; source: DailyHintSource }): string {
  if (hint.source === "LOCAL") {
    return `${API_ROOT.replace(/\/$/, "")}/${hint.url.replace(/^\//, "")}`;
  }
  return hint.url;
}

type DailyStartResponse = {
  sessionId: string;
  date: string;
  hints: DailyHint[];
  hintsRevealed: number;
  totalHints: number;
  estimatedScore: number;
  timePenaltyPerSecond: number;
  hintPenaltyMultiplier: number;
  ranked: boolean;
  completed: boolean;
  gaveUp: boolean;
  finalScore: number | null;
  submittedScore: DailyScore | null;
  elapsedSeconds: number;
};

type Screen =
  | "home"
  | "login"
  | "register"
  | "verify-email"
  | "account"
  | "mode"
  | "faceScope"
  | "setup"
  | "quiz"
  | "complete"
  | "review"
  | "daily"
  | "daily-leaderboard"
  | "daily-done";

type AccountResponse = {
  id: string;
  username: string;
  email: string | null;
  emailVerified: boolean;
  nickname: string;
  createdAtMillis: number;
  token?: string;
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
  const [authEmail, setAuthEmail] = useState("");
  const [authPassword, setAuthPassword] = useState("");
  const [authRestoring, setAuthRestoring] = useState(true);
  const [authLegacyUsername, setAuthLegacyUsername] = useState("");
  const [authLinkLegacy, setAuthLinkLegacy] = useState(false);
  const [authPendingEmail, setAuthPendingEmail] = useState("");
  const [authVerificationToken, setAuthVerificationToken] = useState<string | null>(null);
  const [authVerifyPassword, setAuthVerifyPassword] = useState("");
  const [authNotice, setAuthNotice] = useState<string | null>(null);
  const [dailyRanked, setDailyRanked] = useState(true);
  const [authNickname, setAuthNickname] = useState("");
  const [accountPanelOpen, setAccountPanelOpen] = useState(false);
  const [accountHistory, setAccountHistory] = useState<QuizHistoryItem[]>([]);
  const [accountHistoryLoading, setAccountHistoryLoading] = useState(false);
  const [accountHistoryHasMore, setAccountHistoryHasMore] = useState(false);
  const accountPanelRef = useRef<HTMLDivElement | null>(null);
  const answerInputRef = useRef<HTMLInputElement | null>(null);

  // --- Dev-only state ---
  const [devDailyDates, setDevDailyDates] = useState<string[]>([]);
  const [devSelectedDate, setDevSelectedDate] = useState("");

  // --- Daily challenge state ---
  const [dailySessionId, setDailySessionId] = useState<string | null>(null);
  const [dailyDate, setDailyDate] = useState("");
  const [dailyHints, setDailyHints] = useState<DailyHint[]>([]);
  const [dailyHintsRevealed, setDailyHintsRevealed] = useState(0);
  const [dailyCurrentHintIndex, setDailyCurrentHintIndex] = useState(0);
  const [dailyTotalHints, setDailyTotalHints] = useState(5);
  const [dailyServerScore, setDailyServerScore] = useState(1000);
  const [dailyServerScoreTime, setDailyServerScoreTime] = useState(0);
  const [dailyTimePenaltyPerSecond, setDailyTimePenaltyPerSecond] = useState(1);
  const [dailyLiveScore, setDailyLiveScore] = useState(1000);
  const [dailyAnswer, setDailyAnswer] = useState("");
  const [dailyFeedback, setDailyFeedback] = useState<string | null>(null);
  const [dailyFeedbackStatus, setDailyFeedbackStatus] = useState<"success" | "error" | null>(null);
  const [dailyFinalScore, setDailyFinalScore] = useState<number | null>(null);
  const [dailyElapsedSeconds, setDailyElapsedSeconds] = useState(0);
  const [quizResultDate, setQuizResultDate] = useState("");
  const [dailyGaveUp, setDailyGaveUp] = useState(false);
  const [dailySubmittedScore, setDailySubmittedScore] = useState<DailyScore | null>(null);
  const [dailySubmissionLoading, setDailySubmissionLoading] = useState(false);
  const [dailySubmissionError, setDailySubmissionError] = useState<string | null>(null);
  const [returnToDaily, setReturnToDaily] = useState(false);
  const [leaderboardDate, setLeaderboardDate] = useState(() =>
    new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/London", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date())
  );
  const dailyAuthHeaders = (): Record<string, string> => ({});

  const submitDailyScore = async () => {
    if (!dailySessionId || !currentUser || dailySubmissionLoading) return;
    setDailySubmissionLoading(true);
    setDailySubmissionError(null);
    try {
      const response = await apiFetch(`${API_BASE_URL}/daily/scores`, {
        method: "POST", headers: { "Content-Type": "application/json", ...dailyAuthHeaders() },
        body: JSON.stringify({ sessionId: dailySessionId })
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "Could not submit score.");
      setDailySubmittedScore(data as DailyScore);
    } catch (err) {
      setDailySubmissionError(err instanceof Error ? err.message : "Could not submit score.");
    } finally {
      setDailySubmissionLoading(false);
    }
  };

  useEffect(() => {
    let cancelled = false;
    const query = new URLSearchParams(window.location.search);
    const verificationToken = query.get("verifyEmail");
    if (verificationToken) {
      query.delete("verifyEmail");
      window.history.replaceState(null, "", `${window.location.pathname}${query.size ? `?${query}` : ""}${window.location.hash}`);
      setAuthVerificationToken(verificationToken);
      setScreen("verify-email");
      setAuthRestoring(false);
      return;
    }
    const restore = async () => {
      try {
        const response = await apiFetch(`${API_BASE_URL}/account/me`);
        if (!response.ok) {
          if (response.status !== 401) throw new Error("Could not restore your session. Please try logging in.");
          return;
        }
        const user = await response.json() as AccountResponse;
        if (cancelled) return;
        setCurrentUser(user);
        const attemptId = sessionStorage.getItem("sportsQuizRankedAttempt");
        if (attemptId) {
          const resumed = await apiFetch(`${API_BASE_URL}/daily/sessions/${encodeURIComponent(attemptId)}`);
          if (resumed.ok && !cancelled) applyDailyStart(await resumed.json() as DailyStartResponse);
          else sessionStorage.removeItem("sportsQuizRankedAttempt");
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : "Could not restore session.");
      } finally { if (!cancelled) setAuthRestoring(false); }
    };
    void restore();
    return () => { cancelled = true; };
  }, []);

  const currentQuestion = useMemo(
    () => questions[currentIndex],
    [questions, currentIndex]
  );

  useEffect(() => {
    if (screen !== "daily") return;
    const interval = window.setInterval(() => {
      const elapsed = Math.floor((Date.now() - dailyServerScoreTime) / 1000);
      setDailyLiveScore(Math.max(0, dailyServerScore - elapsed * dailyTimePenaltyPerSecond));
    }, 500);
    return () => window.clearInterval(interval);
  }, [screen, dailyServerScore, dailyServerScoreTime, dailyTimePenaltyPerSecond]);

  useEffect(() => {
    apiFetch(`${API_BASE_URL}/quiz/leagues`)
      .then((r) => r.json())
      .then((data: Record<QuizType, League[]>) => {
        setAvailableLeaguesByType(data);
        setSelectedLeagues(new Set(data.LOGO));
      });
  }, []);

  useEffect(() => {
    apiFetch(`${API_BASE_URL}/daily/dates`)
      .then((r) => r.ok ? r.json() : [])
      .then((dates: string[]) => {
        setDevDailyDates(dates);
        if (dates.length > 0) setDevSelectedDate(dates[dates.length - 1]);
      })
      .catch(() => {});
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
    apiFetch(`${API_BASE_URL}/quiz/results?userId=${currentUser.id}&limit=10`)
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
      const response = await apiFetch(
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
    setReturnToDaily(false);
    sessionStorage.removeItem("sportsQuizRankedAttempt");
    resetQuiz();
    setScreen("home");
  };

  const clearAuthFields = () => {
    setAuthEmail("");
    setAuthPassword("");
    setAuthNickname("");
    setAuthLegacyUsername("");
    setAuthLinkLegacy(false);
    setAuthNotice(null);
    setError(null);
  };

  const finishSignIn = async (user: AccountResponse) => {
    setCurrentUser(user);
    clearAuthFields();
    if (returnToDaily) await startDaily();
    else setScreen("home");
  };

  const login = async () => {
    setLoading(true); setError(null); setAuthNotice(null);
    try {
      const response = await apiFetch(`${API_BASE_URL}/account/${authLinkLegacy ? "link-email" : "login"}`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: authEmail, password: authPassword, ...(authLinkLegacy ? { username: authLegacyUsername } : {}) })
      });
      const payload = await response.json();
      if (!response.ok) {
        if (response.status === 403) { setAuthPendingEmail(authEmail); setAuthPassword(""); setScreen("verify-email"); }
        throw new Error(payload.message || "Login failed.");
      }
      const user = payload as AccountResponse;
      if (!user.emailVerified && !user.token) {
        setAuthPendingEmail(authEmail); setAuthPassword(""); setScreen("verify-email"); return;
      }
      await finishSignIn(user);
    } catch (err) { setError(err instanceof Error ? err.message : "Login failed."); }
    finally { setLoading(false); }
  };

  const register = async () => {
    setLoading(true); setError(null);
    try {
      const response = await apiFetch(`${API_BASE_URL}/account`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: authEmail, password: authPassword, nickname: authNickname })
      });
      const payload = await response.json();
      if (!response.ok) throw new Error(payload.message || "Registration failed.");
      const user = payload as AccountResponse;
      if (!user.emailVerified) { setAuthPendingEmail(authEmail); setAuthPassword(""); setScreen("verify-email"); return; }
      await finishSignIn(user);
    } catch (err) { setError(err instanceof Error ? err.message : "Registration failed."); }
    finally { setLoading(false); }
  };

  const confirmVerification = async () => {
    if (!authVerificationToken) return;
    setLoading(true); setError(null);
    try {
      const response = await apiFetch(`${API_BASE_URL}/account/verify-email`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token: authVerificationToken, password: authVerifyPassword })
      });
      const payload = await response.json();
      if (!response.ok) throw new Error(payload.message || "Could not verify email.");
      setAuthVerificationToken(null); setAuthVerifyPassword("");
      await finishSignIn(payload as AccountResponse);
      setAuthNotice("Email verified. You're ready to play.");
    } catch (err) { setError(err instanceof Error ? err.message : "Could not verify email."); }
    finally { setLoading(false); }
  };

  const resendVerification = async () => {
    setLoading(true); setError(null);
    try {
      const response = await apiFetch(`${API_BASE_URL}/account/verification`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: authPendingEmail })
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "Could not resend verification email.");
      setAuthNotice(data.message);
    } catch (err) { setError(err instanceof Error ? err.message : "Could not resend verification email."); }
    finally { setLoading(false); }
  };

  const logout = async () => {
    setLoading(true); setError(null);
    try {
      const response = await apiFetch(`${API_BASE_URL}/account/logout`, { method: "POST" });
      if (!response.ok) throw new Error("Could not log out. Please try again.");
      sessionStorage.removeItem("sportsQuizRankedAttempt");
      setCurrentUser(null); setAccountPanelOpen(false); setAccountHistory([]); setAccountHistoryHasMore(false); setScreen("home");
    } catch (err) { setError(err instanceof Error ? err.message : "Could not log out."); }
    finally { setLoading(false); }
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
      const response = await apiFetch(`${API_BASE_URL}/quiz/teams`);
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
      const response = await apiFetch(`${API_BASE_URL}/quiz`, {
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
      const response = await apiFetch(`${API_BASE_URL}/quiz/answer`, {
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
            setQuizResultDate(new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/London", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date()));
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
      const response = await apiFetch(`${API_BASE_URL}/quiz/skip`, {
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
          setQuizResultDate(new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/London", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date()));
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
      const response = await apiFetch(`${API_BASE_URL}/ai/quiz-summary`, {
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
      const response = await apiFetch(`${API_BASE_URL}/quiz/${quizId}`);
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

  const resetDaily = () => {
    setDailySubmittedScore(null);
    setDailySubmissionError(null);
    setReturnToDaily(false);
    setDailySessionId(null);
    setDailyDate("");
    setDailyHints([]);
    setDailyHintsRevealed(0);
    setDailyCurrentHintIndex(0);
    setDailyTotalHints(5);
    setDailyServerScore(1000);
    setDailyServerScoreTime(0);
    setDailyLiveScore(1000);
    setDailyAnswer("");
    setDailyFeedback(null);
    setDailyFeedbackStatus(null);
    setDailyFinalScore(null);
    setDailyGaveUp(false);
    setDailyElapsedSeconds(0);
  };

  const applyDailyStart = (payload: DailyStartResponse) => {
    setDailySubmittedScore(payload.submittedScore); setDailySubmissionError(null); setReturnToDaily(false);
    setDailySessionId(payload.sessionId); setDailyDate(payload.date); setDailyHints(payload.hints);
    setDailyHintsRevealed(payload.hintsRevealed); setDailyCurrentHintIndex(Math.max(0, payload.hintsRevealed - 1));
    setDailyTotalHints(payload.totalHints); setDailyServerScore(payload.estimatedScore); setDailyServerScoreTime(Date.now());
    setDailyTimePenaltyPerSecond(payload.timePenaltyPerSecond); setDailyLiveScore(payload.estimatedScore);
    setDailyAnswer(""); setDailyFeedback(null); setDailyFeedbackStatus(null); setDailyFinalScore(payload.finalScore);
    setDailyGaveUp(payload.gaveUp); setDailyRanked(payload.ranked);
    setDailyElapsedSeconds(payload.elapsedSeconds);
    if (payload.ranked) sessionStorage.setItem("sportsQuizRankedAttempt", payload.sessionId);
    else sessionStorage.removeItem("sportsQuizRankedAttempt");
    setScreen(payload.completed ? "daily-done" : "daily");
  };

  const startDaily = async (date?: string, practice = false) => {
    if (authRestoring) return;
    setLoading(true); setError(null);
    try {
      const response = await apiFetch(`${API_BASE_URL}/daily`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ date, practice })
      });
      const payload = await response.json();
      if (!practice && response.status === 401) {
        clearAuthFields(); setReturnToDaily(true); setScreen("login"); return;
      }
      if (!response.ok) throw new Error(payload.message || "No daily challenge available today.");
      applyDailyStart(payload as DailyStartResponse);
    } catch (err) { setError(err instanceof Error ? err.message : "Could not start challenge."); }
    finally { setLoading(false); }
  };

  const requestDailyHint = async () => {
    if (!dailySessionId || loading) return;
    setLoading(true);
    setError(null);
    try {
      const response = await apiFetch(`${API_BASE_URL}/daily/hint`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...dailyAuthHeaders() },
        body: JSON.stringify({ sessionId: dailySessionId })
      });
      if (!response.ok) {
        const payload = (await response.json()) as { message?: string };
        throw new Error(payload.message || "Could not get next hint.");
      }
      const payload = (await response.json()) as {
        hints: DailyHint[];
        hintsRevealed: number;
        totalHints: number;
        estimatedScore: number;
      };
      const now = Date.now();
      setDailyHints(payload.hints);
      setDailyHintsRevealed(payload.hintsRevealed);
      setDailyCurrentHintIndex(payload.hintsRevealed - 1);
      setDailyTotalHints(payload.totalHints);
      setDailyServerScore(payload.estimatedScore);
      setDailyServerScoreTime(now);
      setDailyLiveScore(payload.estimatedScore);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error.");
    } finally {
      setLoading(false);
    }
  };

  const submitDailyAnswer = async () => {
    if (!dailySessionId || loading) return;
    const trimmed = dailyAnswer.trim();
    if (!trimmed) {
      setError("Enter a name before submitting.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await apiFetch(`${API_BASE_URL}/daily/answer`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...dailyAuthHeaders() },
        body: JSON.stringify({ sessionId: dailySessionId, answer: trimmed })
      });
      if (!response.ok) {
        const payload = (await response.json()) as { message?: string };
        throw new Error(payload.message || "Could not submit answer.");
      }
      const payload = (await response.json()) as {
        correct: boolean;
        score: number | null;
        elapsedSeconds: number;
        submittedScore: DailyScore | null;
      };
      if (payload.correct) {
        setDailySubmittedScore(payload.submittedScore);
        setDailyFinalScore(payload.score ?? 0);
        setDailyElapsedSeconds(payload.elapsedSeconds);
        setDailyGaveUp(false);
        setScreen("daily-done");
      } else {
        setDailyFeedbackStatus("error");
        setDailyFeedback("Not quite — try again!");
        setDailyAnswer("");
        window.setTimeout(() => {
          setDailyFeedback(null);
          setDailyFeedbackStatus(null);
        }, 1200);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unexpected error.");
    } finally {
      setLoading(false);
    }
  };

  const giveUpDaily = async () => {
    if (!dailySessionId || loading) return;
    setLoading(true);
    setError(null);
    try {
      const response = await apiFetch(`${API_BASE_URL}/daily/give-up`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...dailyAuthHeaders() },
        body: JSON.stringify({ sessionId: dailySessionId })
      });
      const payload = await response.json();
      if (!response.ok) throw new Error(payload.message || "Could not end challenge.");
      setDailySubmittedScore(payload.submittedScore ?? null);
      setDailyGaveUp(true);
      setDailyElapsedSeconds(payload.elapsedSeconds);
      setDailyFinalScore(0);
      setScreen("daily-done");
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
    <div className={`app app--${screen}`}>
      <a className="skip-link" href="#main-content">Skip to content</a>
      <header className="app__header">
        <button className="brand" onClick={goHome} aria-label="Sports Quiz home">Sports Quiz<span>.</span></button>
        {screen !== "quiz" && screen !== "daily" && <nav className="app__nav" aria-label="Main navigation">
          <button aria-current={screen === "home" ? "page" : undefined} onClick={goHome}>Play</button>
          <button aria-current={screen === "daily-leaderboard" ? "page" : undefined}
            onClick={() => { setError(null); setScreen("daily-leaderboard"); }}>Leaderboard</button>
        </nav>}
        <div className="app__header-actions">
          {(screen === "quiz" || screen === "daily") && (
            <button className="ghost-button" onClick={goHome}>
              Exit Quiz
            </button>
          )}
          {currentUser ? (
            <div className="account-menu" ref={accountPanelRef}>
              <button
                className="ghost-button account-menu__trigger"
                aria-expanded={accountPanelOpen}
                aria-controls="account-menu-panel"
                onClick={() => setAccountPanelOpen((prev) => !prev)}
              >
                <span className="account-avatar" aria-hidden="true">{currentUser.nickname.slice(0, 1).toUpperCase()}</span>
                {currentUser.nickname}
              </button>
              {accountPanelOpen && (
                <div className="account-panel" id="account-menu-panel">
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
                    <button
                      className="ghost-button account-panel__footer-btn"
                      onClick={() => { setAccountPanelOpen(false); setScreen("account"); }}
                    >
                      My Account
                    </button>
                    <button className="ghost-button account-panel__footer-btn account-panel__logout" onClick={logout}>
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

      <main className="app__main" id="main-content" tabIndex={-1}>
        {screen === "home" && (
          <section className="home" aria-labelledby="home-title">
            <div className="home__intro">
              <div>
                <p className="eyebrow"><span className="status-dot" /> A little competition. Every day.</p>
                <h1 id="home-title">Know the game.<br /><span>Prove it.</span></h1>
                <p className="supporting">Big leagues. Familiar faces. How much do you really know?</p>
              </div>
              <img className="home__emblem" src={logo} alt="Sports Quiz" />
            </div>
            <div className="home__games">
              <article className="daily-feature">
                <div className="daily-feature__top"><span className="eyebrow">The daily challenge</span><span className="edition-tag">A fresh test every day</span></div>
                <div className="daily-feature__body">
                  <div>
                    <h2>Athlete<br />of the day<span>.</span></h2>
                    <p>One athlete. A few clues.<br />Make them count.</p>
                    <button className="primary-button" onClick={() => startDaily()} disabled={loading || authRestoring}>
                      {loading ? "Getting your challenge..." : "Play today's challenge"}<span aria-hidden="true">↗</span>
                    </button>
                    <p className="supporting">Sign in before playing to join the leaderboard.</p>
                  </div>
                  <div className="daily-feature__art" aria-hidden="true"><div className="court-ring" /><span>?</span><div className="court-line" /></div>
                </div>
                <div className="daily-feature__footer"><span>Fewer clues. Faster answers. Higher scores.</span><span aria-hidden="true">01 / DAILY</span></div>
              </article>
              <div className="home__secondary">
                <button className="game-tile" onClick={() => setScreen("mode")}>
                  <span className="game-tile__top"><span className="eyebrow">Your game, your rules</span><span className="tile-arrow" aria-hidden="true">↗</span></span>
                  <span className="game-tile__title">Build a quiz</span>
                  <span className="game-tile__description">Logos or faces. Pick your leagues and find your level.</span>
                  <span className="game-tile__bottom">Custom quiz<span aria-hidden="true">→</span></span>
                </button>
                <button className="game-tile game-tile--leaderboard" onClick={() => { setError(null); setScreen("daily-leaderboard"); }}>
                  <span className="game-tile__top"><span className="eyebrow">See how you stack up</span><span className="tile-arrow" aria-hidden="true">↗</span></span>
                  <span className="game-tile__title">The leaderboard</span>
                  <span className="game-tile__description">Today's contenders. One score per player. Every point counts.</span>
                  <span className="game-tile__bottom">Explore the standings<span aria-hidden="true">→</span></span>
                </button>
              </div>
            </div>
            <div className="home__footnote"><span>For the fans who know.</span><span>NBA / NFL / MLB / NHL</span></div>
            {authNotice && <p className="message success">{authNotice}</p>}
            {error && <p className="message error" role="alert">{error}</p>}
            {devDailyDates.length > 0 && (
              <div className="dev-daily">
                <span className="dev-daily__label">Practice</span>
                <select
                  className="dev-daily__select"
                  value={devSelectedDate}
                  onChange={(e) => setDevSelectedDate(e.target.value)}
                >
                  {devDailyDates.map((d) => (
                    <option key={d} value={d}>{d}</option>
                  ))}
                </select>
                <button
                  className="ghost-button dev-daily__play"
                  onClick={() => startDaily(devSelectedDate, true)}
                  disabled={loading || !devSelectedDate}
                >
                  Practice
                </button>
              </div>
            )}
          </section>
        )}

        {screen === "login" && (
          <section className="card hero">
            <p className="eyebrow">Welcome back</p>
            <h1>{authLinkLegacy ? "Link your email" : "Log in"}</h1>
            {returnToDaily && <p className="supporting">Log in before starting your ranked attempt. Returning players resume their existing challenge.</p>}
            {authLinkLegacy && <p className="supporting">Link an older username account to an email address to keep your existing scores.</p>}
            <form
              className="quiz__form"
              onSubmit={(e) => { e.preventDefault(); login(); }}
            >
              {authLinkLegacy && <input aria-label="Existing username" placeholder="Existing username" value={authLegacyUsername}
                onChange={(event) => setAuthLegacyUsername(event.target.value)} required disabled={loading} />}
              <input
                type="text"
                aria-label="Email address"
                autoComplete="username"
                placeholder={import.meta.env.DEV ? "Email (or user in development)" : "Email address"}
                value={authEmail}
                onChange={(e) => setAuthEmail(e.target.value)}
                disabled={loading}
                autoFocus
              />
              <input
                type="password"
                aria-label="Password"
                autoComplete="current-password"
                placeholder="Password"
                value={authPassword}
                onChange={(e) => setAuthPassword(e.target.value)}
                disabled={loading}
              />
              <button className="ghost-button" type="button" onClick={() => { setAuthLinkLegacy(!authLinkLegacy); setError(null); }}>
                {authLinkLegacy ? "Back to email login" : "Link an existing username account"}
              </button>
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
                type="email"
                required
                aria-label="Email address"
                autoComplete="username"
                placeholder="Email address"
                value={authEmail}
                onChange={(e) => setAuthEmail(e.target.value)}
                disabled={loading}
                autoFocus
              />
              <input
                type="password"
                aria-label="Password (at least 8 characters)"
                autoComplete="new-password"
                placeholder="Password (min. 8 characters)"
                value={authPassword}
                onChange={(e) => setAuthPassword(e.target.value)}
                disabled={loading}
              />
              <input
                type="text"
                aria-label="Nickname (your display name)"
                autoComplete="nickname"
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

        {screen === "verify-email" && (
          <section className="card hero">
            <p className="eyebrow">One last step</p><h1>Check your email</h1>
            <p className="supporting">{authVerificationToken ? "Confirm the password you chose for this account to finish verifying your email." : "Use the verification link in your inbox to activate your account. Check your spam folder too."}</p>
            {authVerificationToken && <form className="quiz__form" onSubmit={(event) => { event.preventDefault(); void confirmVerification(); }}>
              <input type="password" aria-label="Account password" autoComplete="current-password" placeholder="Your account password"
                value={authVerifyPassword} onChange={(event) => setAuthVerifyPassword(event.target.value)} required disabled={loading} />
              <button className="primary-button" disabled={loading} type="submit">{loading ? "Verifying..." : "Verify email and log in"}</button>
            </form>}
            <form className="quiz__form" onSubmit={(event) => { event.preventDefault(); void resendVerification(); }}>
              <input type="email" aria-label="Email address" placeholder="Email address" required value={authPendingEmail}
                onChange={(event) => setAuthPendingEmail(event.target.value)} disabled={loading} />
              <button className="primary-button" disabled={loading || !authPendingEmail} type="submit">{loading ? "Sending..." : "Resend verification email"}</button>
              <button className="ghost-button" type="button" onClick={() => { clearAuthFields(); setScreen("login"); }}>Back to login</button>
            </form>
            {authNotice && <p className="message success">{authNotice}</p>}
            {error && <p className="message error" role="alert">{error}</p>}
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
                aria-label={quizType === "LOGO" ? "Team name" : "Player name"}
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

        {screen === "daily" && (
          <section className="card daily">
            <div className="daily__header">
              <div><p className="eyebrow">{dailyRanked ? "Ranked challenge" : "Unranked challenge"} / {dailyDate}</p><h1>Who's the athlete?</h1></div>
              <div className="daily__score"><span className="daily__score-label"><span className="status-dot" />Live score</span>
                <strong>{dailyLiveScore.toLocaleString()}<small>pts</small></strong></div>
            </div>
            <div className="daily__progress" aria-label={`${dailyHintsRevealed} of ${dailyTotalHints} clues revealed`}>
              {Array.from({ length: dailyTotalHints }, (_, index) => <span key={index} className={index < dailyHintsRevealed ? "is-revealed" : ""} />)}
            </div>
            <div className="daily__hint-viewer">
              <div className="daily__hint-nav">
                <button
                  className="ghost-button daily__hint-nav-btn"
                  type="button"
                  onClick={() => setDailyCurrentHintIndex((i) => i - 1)}
                  disabled={dailyCurrentHintIndex === 0}
                >
                  ‹ Prev
                </button>
                <span className="daily__hint-counter">
                  Hint {dailyCurrentHintIndex + 1} of {dailyTotalHints}
                </span>
                {dailyCurrentHintIndex < dailyHintsRevealed - 1 ? (
                  <button
                    className="ghost-button daily__hint-nav-btn"
                    type="button"
                    onClick={() => setDailyCurrentHintIndex((i) => i + 1)}
                  >
                    Next ›
                  </button>
                ) : dailyHintsRevealed < dailyTotalHints ? (
                  <button
                    className="ghost-button daily__hint-nav-btn"
                    type="button"
                    onClick={requestDailyHint}
                    disabled={loading}
                  >
                    {loading ? "..." : "Next Hint ›"}
                  </button>
                ) : (
                  <span className="daily__hint-nav-btn daily__hint-nav-btn--placeholder" />
                )}
              </div>
              <div className="daily__hint">
                {(() => {
                  const hint = dailyHints[dailyCurrentHintIndex];
                  if (!hint) return null;
                  if (hint.type === "IMAGE") {
                    const src = resolveHintUrl(hint);
                    return (
                      <img
                        className="daily__hint-media"
                        src={src}
                        alt={`Hint ${dailyCurrentHintIndex + 1}`}
                      />
                    );
                  }
                  if (hint.type === "AUDIO") {
                    const src = resolveHintUrl(hint);
                    return (
                      <audio
                        className="daily__hint-media daily__hint-media--audio"
                        controls
                        src={src}
                        key={src}
                      />
                    );
                  }
                  if (hint.type === "VIDEO") {
                    const src = resolveHintUrl(hint);
                    return (
                      <video
                        className="daily__hint-media"
                        controls
                        src={src}
                        key={src}
                      />
                    );
                  }
                  if (hint.type === "TEXT") {
                    return (
                      <figure className="daily__hint-quote">
                        <blockquote className="daily__hint-text">
                          <p className="daily__hint-text__body">{hint.value}</p>
                        </blockquote>
                        {hint.attribution && <figcaption className="daily__hint-attribution">— {hint.attribution}</figcaption>}
                      </figure>
                    );
                  }
                  if (hint.type === "STAT_LINE" && hint.statLine) {
                    return (
                      <div className="daily__hint-statline">
                        <p className="daily__hint-statline__label">{hint.statLine.label}</p>
                        <ul className={`daily__hint-statline__grid${hint.statLine.stats.length === 3 ? " daily__hint-statline__grid--three" : ""}`}>
                          {hint.statLine.stats.map((s) => (
                            <li key={s.name} className="daily__hint-statline__stat">
                              <span className="daily__hint-statline__stat-value">{s.value}</span>
                              <span className="daily__hint-statline__stat-name">{s.name}</span>
                            </li>
                          ))}
                        </ul>
                      </div>
                    );
                  }
                  if (hint.type === "ACCOMPLISHMENTS") {
                    return <DailyAccomplishments accomplishments={hint.accomplishments} />;
                  }
                  return null;
                })()}
              </div>
            </div>
            <form
              className="quiz__form"
              onSubmit={(e) => { e.preventDefault(); submitDailyAnswer(); }}
            >
              <input
                type="text"
                aria-label="Athlete name"
                placeholder="Name the athlete..."
                value={dailyAnswer}
                onChange={(e) => setDailyAnswer(e.target.value)}
                disabled={loading}
                autoFocus
              />
              <div className="quiz__actions">
                <button className="primary-button" type="submit" disabled={loading}>
                  {loading ? "Checking..." : "Submit"}
                </button>
                <button
                  className="ghost-button"
                  type="button"
                  onClick={giveUpDaily}
                  disabled={loading}
                >
                  Give Up
                </button>
              </div>
            </form>
            {dailyFeedback && (
              <div className={`message ${dailyFeedbackStatus ?? "error"}`}>
                {dailyFeedback}
              </div>
            )}
            {error && <div className="message error">{error}</div>}
          </section>
        )}

        {screen === "daily-done" && (
          <section className="card hero">
            <p className="eyebrow">Daily Challenge — {dailyDate}</p>
            <h1>{dailyGaveUp ? "Better luck tomorrow!" : "Correct!"}</h1>
            <ResultShare key={dailySessionId} result={{ mode: "daily", date: dailyDate,
              score: dailySubmittedScore?.score ?? dailyFinalScore ?? 0,
              hintsUsed: dailySubmittedScore?.hintsRevealed ?? dailyHintsRevealed, totalHints: dailyTotalHints,
              elapsedSeconds: dailySubmittedScore?.elapsedSeconds ?? dailyElapsedSeconds,
              ranked: dailyRanked, gaveUp: dailyGaveUp }} />
            <div className="daily-submit">
              {!dailyRanked ? <p className="supporting">Your result is unranked. Only challenges started while signed in count toward the leaderboard.</p>
                : dailySubmittedScore ? <p className="message success">Saved to your daily history and leaderboard. This is your score for the day.</p>
                : <><p className="supporting">Your attempt is complete. Retry saving if the connection was interrupted.</p>
                  <button className="primary-button" disabled={dailySubmissionLoading} onClick={submitDailyScore}>Retry saving score</button></>}
              {dailySubmissionError && <p className="message error" role="alert">{dailySubmissionError}</p>}
            </div>
            <div className="stack">
              <button className="primary-button" disabled={dailySubmissionLoading} onClick={() => { setLeaderboardDate(dailyDate); setScreen("daily-leaderboard"); }}>
                View daily leaderboard
              </button>
              <button className="ghost-button" onClick={() => { resetDaily(); goHome(); }}>
                Back Home
              </button>
            </div>
          </section>
        )}

        {screen === "daily-leaderboard" && (
          <section className="card leaderboard-page">
            <div className="page-heading"><div><p className="eyebrow">The daily standings</p>
            <h1>Every point counts<span className="accent">.</span></h1>
            <p className="supporting">One challenge. One score per player. See who set the pace.</p></div></div>
            <div className="leaderboard-toolbar">
              <label htmlFor="leaderboard-date">Challenge date</label>
              <input id="leaderboard-date" type="date" value={leaderboardDate}
                onChange={(event) => { if (event.target.value) setLeaderboardDate(event.target.value); }} />
              <button className="ghost-button" onClick={goHome}>Back Home</button>
            </div>
            <DailyScores apiBase={API_BASE_URL} date={leaderboardDate} signedIn={!!currentUser} />
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
            {reviewData ? <ResultShare key={quizId} result={{ mode: quizType === "LOGO" ? "logos" : "faces",
              date: quizResultDate, score: reviewData.score,
              hintsUsed: reviewData.questions.filter(question => question.hinted).length,
              elapsedSeconds: reviewData.elapsedSeconds, correctCount: reviewData.correctCount,
              totalQuestions: reviewData.totalQuestions }} /> : <p role="status">Preparing your result…</p>}
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
        {screen === "account" && currentUser && (
          <section className="card account-page">
            <div className="account-page__header">
              <h2>My Account</h2>
              <button className="ghost-button" onClick={goHome}>
                Back Home
              </button>
            </div>

            <div className="account-page__profile">
              <div className="account-page__field">
                <span className="account-page__label">Nickname</span>
                <span className="account-page__value">{currentUser.nickname}</span>
              </div>
              <div className="account-page__field">
                <span className="account-page__label">Email</span>
                <span className="account-page__value">{currentUser.email ?? currentUser.username}</span>
              </div>
              <div className="account-page__field">
                <span className="account-page__label">Member since</span>
                <span className="account-page__value">{formatDate(currentUser.createdAtMillis)}</span>
              </div>
            </div>

            <div className="account-page__history">
              <h3 className="account-page__section-title">Daily Challenge History</h3>
              <DailyScores apiBase={API_BASE_URL} signedIn history />
            </div>

            <div className="account-page__history">
              <h3 className="account-page__section-title">Quiz History</h3>
              {accountHistoryLoading && accountHistory.length === 0 ? (
                <p className="account-page__empty">Loading...</p>
              ) : accountHistory.length === 0 ? (
                <p className="account-page__empty">No quizzes yet. Start a quiz to see your history here.</p>
              ) : (
                <>
                  <div className="account-page__history-list">
                    {accountHistory.map((item) => (
                      <div key={item.quizId} className="account-page__history-item">
                        <div className="account-page__history-tags">
                          <span className="tag">{item.quizType === "LOGO" ? "Logos" : "Faces"}</span>
                          <span className="tag">{item.difficulty.charAt(0) + item.difficulty.slice(1).toLowerCase()}</span>
                        </div>
                        <div className="account-page__history-score">{item.score} pts</div>
                        <div className="account-page__history-date">{formatDate(item.completedAtMillis)}</div>
                      </div>
                    ))}
                  </div>
                  {accountHistoryHasMore && (
                    <button
                      className="ghost-button account-page__load-more"
                      onClick={loadMoreHistory}
                      disabled={accountHistoryLoading}
                    >
                      {accountHistoryLoading ? "Loading..." : "Load more"}
                    </button>
                  )}
                </>
              )}
            </div>
          </section>
        )}
      </main>
    </div>
  );
}
