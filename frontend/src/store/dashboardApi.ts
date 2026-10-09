import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import type { MacroRange } from './dashboardSlice'

export interface TodaySummary {
  metabolicDate: string
  targetCalories: number | null
  consumedCalories: number
  remainingCalories: number | null
  entryCount: number
}

export interface DailyMacros {
  date: string
  proteinG: number
  carbsG: number
  fatG: number
  calories: number
}

export interface WeighIn {
  date: string
  weightLbs: number
  /** Time of day of that day's (earliest) reading, "HH:mm:ss". */
  time: string | null
}

export interface TrendPoint {
  date: string
  value: number
}

export interface GoalLine {
  startDate: string
  startWeightLbs: number
  dailyRateLbs: number
}

export interface WeightTrendResponse {
  actual: WeighIn[]
  smoothed: TrendPoint[]
  goal: GoalLine | null
}

export interface TdeeStatus {
  estimatedCalories: number | null
  standardErrorCalories: number | null
  windowDays: number | null
  loggedDays: number | null
  weightChangeLbs: number | null
  caveat: string | null
  unavailableReason: string | null
}

export interface MeasurementView {
  id: number
  measuredAt: string
  waistIn: number | null
  neckIn: number | null
  hipIn: number | null
  /** US Navy body-fat estimate, or null when the profile or a needed measurement is missing. */
  bodyFatPct: number | null
  /** The estimate used an earlier session's neck (or hip) because this one didn't measure it. */
  neckCarried: boolean
  hipCarried: boolean
}

export interface MeasurementsResponse {
  measurements: MeasurementView[]
  /** Sex and height are configured, so body fat can be estimated. */
  profileComplete: boolean
}

export interface BloodPressureReading {
  timestamp: string
  systolic: number
  diastolic: number
  bpm: number | null
}

// The read-only dashboard data - header summary, macro chart, weight trend + TDEE, blood pressure -
// cached and deduped by RTK Query, so the desktop sidebar and the mobile chart sheets share one
// fetch. Anything that can log or delete an entry calls invalidateAfterLog() instead of keeping its
// own list of things to refetch; only queries something is currently showing actually refetch.
export const dashboardApi = createApi({
  reducerPath: 'dashboardApi',
  baseQuery: fetchBaseQuery({ baseUrl: '/api' }),
  tagTypes: ['Summary', 'Macros', 'WeightTrend', 'Tdee', 'BloodPressure', 'Measurements'],
  keepUnusedDataFor: 300,
  endpoints: (builder) => ({
    getSummary: builder.query<TodaySummary, void>({
      query: () => 'summary/today',
      providesTags: ['Summary'],
    }),
    getMacros: builder.query<DailyMacros[], MacroRange>({
      query: (days) => `dashboard/macros?days=${days}`,
      providesTags: ['Macros'],
    }),
    getWeightTrend: builder.query<WeightTrendResponse, void>({
      query: () => 'dashboard/weight-trend',
      providesTags: ['WeightTrend'],
    }),
    getTdee: builder.query<TdeeStatus, void>({
      query: () => 'dashboard/tdee',
      providesTags: ['Tdee'],
    }),
    getBloodPressure: builder.query<BloodPressureReading[], MacroRange>({
      query: (days) => `dashboard/blood-pressure?days=${days}`,
      providesTags: ['BloodPressure'],
    }),
    // The newest reading regardless of range (204 → null when none are imported), so the page can
    // show how current the data is even when it's older than the charted 7 or 30 days.
    getMeasurements: builder.query<MeasurementsResponse, void>({
      query: () => 'measurements',
      providesTags: ['Measurements'],
    }),
    getLatestBloodPressure: builder.query<BloodPressureReading | null, void>({
      query: () => 'dashboard/blood-pressure/latest',
      providesTags: ['BloodPressure'],
    }),
  }),
})

export const {
  useGetSummaryQuery,
  useGetMacrosQuery,
  useGetWeightTrendQuery,
  useGetTdeeQuery,
  useGetBloodPressureQuery,
  useGetLatestBloodPressureQuery,
  useGetMeasurementsQuery,
} = dashboardApi

/** Marks everything a log, correction, or delete can change as stale. */
export const invalidateAfterLog = () =>
  dashboardApi.util.invalidateTags(['Summary', 'Macros', 'WeightTrend', 'Tdee', 'BloodPressure', 'Measurements'])
