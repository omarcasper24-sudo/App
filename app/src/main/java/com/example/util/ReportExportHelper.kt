package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.PlaceLocation
import com.example.data.model.SleepLog
import com.example.data.model.TripRecord
import com.example.data.model.WorkSession
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExportHelper {

    fun exportPdfReport(
        context: Context,
        periodTitle: String,
        trips: List<TripRecord>,
        places: List<PlaceLocation>,
        sleepLogs: List<SleepLog>,
        workSessions: List<WorkSession>
    ): Result<File> {
        return try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.rgb(11, 19, 43)
                textSize = 20f
                isFakeBoldText = true
            }
            val sectionPaint = Paint().apply {
                color = Color.rgb(0, 103, 120)
                textSize = 14f
                isFakeBoldText = true
            }
            val bodyPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 11f
            }
            val linePaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                strokeWidth = 1.5f
            }

            var y = 48f
            canvas.drawText("LifeTrack - Smart Location & Activity Report ($periodTitle)", 40f, y, titlePaint)
            y += 22f
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            canvas.drawText("Generated: $dateStr", 40f, y, bodyPaint)
            y += 16f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 26f

            // Summary Metrics
            val totalKm = trips.sumOf { it.distanceKm }
            val walkKm = trips.filter { it.transportMode == "WALKING" }.sumOf { it.distanceKm }
            val driveKm = trips.filter { it.transportMode == "DRIVING" }.sumOf { it.distanceKm }
            val avgSleepHrs = if (sleepLogs.isNotEmpty()) sleepLogs.map { it.durationMinutes }.average() / 60.0 else 0.0
            val totalWorkHrs = workSessions.filter { it.sessionType != "PERSONAL" }.sumOf { it.durationMinutes } / 60.0

            canvas.drawText("1. Executive Summary", 40f, y, sectionPaint)
            y += 20f
            canvas.drawText(String.format(Locale.US, "• Total Distance Traveled: %.2f km (%d trips)", totalKm, trips.size), 50f, y, bodyPaint)
            y += 16f
            canvas.drawText(String.format(Locale.US, "• Walking Distance: %.2f km | Driving Distance: %.2f km", walkKm, driveKm), 50f, y, bodyPaint)
            y += 16f
            canvas.drawText(String.format(Locale.US, "• Total Working Hours: %.1f hrs | Avg Estimated Sleep: %.1f hrs/night", totalWorkHrs, avgSleepHrs), 50f, y, bodyPaint)
            y += 26f

            // Top Visited Places
            canvas.drawText("2. Top Frequently Visited Locations", 40f, y, sectionPaint)
            y += 20f
            places.sortedByDescending { it.visitCount }.take(8).forEachIndexed { idx, place ->
                canvas.drawText(
                    String.format(
                        Locale.US,
                        "%d. %s [%s] - %d visits (%.1f hrs total)",
                        idx + 1,
                        place.name,
                        place.category,
                        place.visitCount,
                        place.totalDurationMinutes / 60.0
                    ),
                    50f,
                    y,
                    bodyPaint
                )
                y += 16f
            }
            y += 14f

            // Recent Trips
            canvas.drawText("3. Movement Log Breakdown", 40f, y, sectionPaint)
            y += 20f
            trips.take(12).forEach { trip ->
                if (y < 780f) {
                    canvas.drawText(
                        String.format(
                            Locale.US,
                            "• [%s] %s: %s -> %s (%.2f km, %d min)",
                            trip.dateKey,
                            trip.transportMode,
                            trip.startLabel,
                            trip.endLabel,
                            trip.distanceKm,
                            trip.durationMinutes
                        ),
                        50f,
                        y,
                        bodyPaint
                    )
                    y += 15f
                }
            }

            y = 810f
            canvas.drawText("Disclaimer: Sleep & activity figures are sensor-derived estimates, not medical measurements.", 40f, y, bodyPaint)

            pdfDocument.finishPage(page)

            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val file = File(reportsDir, "LifeTrack_${periodTitle.replace(" ", "_")}_Report.pdf")
            FileOutputStream(file).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            shareExportedFile(context, file, "application/pdf")
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportExcelCsvReport(
        context: Context,
        periodTitle: String,
        trips: List<TripRecord>,
        places: List<PlaceLocation>,
        sleepLogs: List<SleepLog>,
        workSessions: List<WorkSession>
    ): Result<File> {
        return try {
            val sb = StringBuilder()
            // UTF-8 BOM for Microsoft Excel compatibility with Arabic & English text
            sb.append("\uFEFF")
            sb.append("Section,Date,CategoryOrMode,StartOrName,EndOrDetails,DistanceKm,DurationMinutes\n")

            trips.forEach { t ->
                sb.append("TRIP,${t.dateKey},${t.transportMode},\"${t.startLabel}\",\"${t.endLabel}\",${t.distanceKm},${t.durationMinutes}\n")
            }
            places.forEach { p ->
                sb.append("PLACE,ALL,${p.category},\"${p.name}\",\"Visits: ${p.visitCount}\",0.0,${p.totalDurationMinutes}\n")
            }
            sleepLogs.forEach { s ->
                sb.append("SLEEP_ESTIMATE,${s.dateKey},${s.scheduleRegularity},\"Score: ${s.sleepQualityScore}\",\"${s.notes}\",0.0,${s.durationMinutes}\n")
            }
            workSessions.forEach { w ->
                sb.append("WORK_SESSION,${w.dateKey},${w.sessionType},\"${w.workplaceName}\",\"Manual: ${w.isManuallyCorrected}\",0.0,${w.durationMinutes}\n")
            }

            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val file = File(reportsDir, "LifeTrack_${periodTitle.replace(" ", "_")}_Data.csv")
            file.writeText(sb.toString(), Charsets.UTF_8)
            shareExportedFile(context, file, "text/csv")
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun shareExportedFile(context: Context, file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, file.name).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Ignored in headless test environments
        }
    }
}
