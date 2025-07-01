package com.example.arvideo

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class ImageMatcher {
    
    companion object {
        private const val MATCH_THRESHOLD = 0.1f  // Test için çok düşük eşik
        private const val SAMPLE_SIZE = 30 // Daha küçük sample size - daha hızlı
    }
    
    /**
     * İki bitmap arasındaki benzerlik oranını hesaplar
     * @param target Hedef resim
     * @param source Karşılaştırılacak resim
     * @return 0.0 ile 1.0 arasında benzerlik oranı
     */
    fun calculateSimilarity(target: Bitmap?, source: Bitmap?): Float {
        if (target == null || source == null) return 0f
        
        // Resimleri aynı boyuta getir
        val targetScaled = Bitmap.createScaledBitmap(target, SAMPLE_SIZE, SAMPLE_SIZE, true)
        val sourceScaled = Bitmap.createScaledBitmap(source, SAMPLE_SIZE, SAMPLE_SIZE, true)
        
        var totalDifference = 0.0
        val totalPixels = SAMPLE_SIZE * SAMPLE_SIZE
        
        for (x in 0 until SAMPLE_SIZE) {
            for (y in 0 until SAMPLE_SIZE) {
                val targetPixel = targetScaled.getPixel(x, y)
                val sourcePixel = sourceScaled.getPixel(x, y)
                
                val difference = calculatePixelDifference(targetPixel, sourcePixel)
                totalDifference += difference
            }
        }
        
        // Ortalama farkı hesapla ve benzerlik oranına çevir
        val averageDifference = totalDifference / totalPixels
        val similarity = 1.0 - (averageDifference / (255.0 * sqrt(3.0))) // RGB için normalize et
        
        return similarity.toFloat().coerceIn(0f, 1f)
    }
    
    /**
     * İki pixel arasındaki renk farkını hesaplar
     */
    private fun calculatePixelDifference(pixel1: Int, pixel2: Int): Double {
        val r1 = Color.red(pixel1)
        val g1 = Color.green(pixel1)
        val b1 = Color.blue(pixel1)
        
        val r2 = Color.red(pixel2)
        val g2 = Color.green(pixel2)
        val b2 = Color.blue(pixel2)
        
        val rDiff = abs(r1 - r2)
        val gDiff = abs(g1 - g2)
        val bDiff = abs(b1 - b2)
        
        return sqrt((rDiff * rDiff + gDiff * gDiff + bDiff * bDiff).toDouble())
    }
    
    /**
     * Benzerlik oranının eşik değerini geçip geçmediğini kontrol eder
     */
    fun isMatch(similarity: Float): Boolean {
        return similarity >= MATCH_THRESHOLD
    }
    
    /**
     * Histogram tabanlı benzerlik hesaplama (alternatif method)
     */
    fun calculateHistogramSimilarity(target: Bitmap?, source: Bitmap?): Float {
        if (target == null || source == null) return 0f
        
        val targetHist = calculateHistogram(target)
        val sourceHist = calculateHistogram(source)
        
        return compareHistograms(targetHist, sourceHist)
    }
    
    private fun calculateHistogram(bitmap: Bitmap): IntArray {
        val histogram = IntArray(256) // Grayscale histogram
        val scaled = Bitmap.createScaledBitmap(bitmap, SAMPLE_SIZE, SAMPLE_SIZE, true)
        
        for (x in 0 until SAMPLE_SIZE) {
            for (y in 0 until SAMPLE_SIZE) {
                val pixel = scaled.getPixel(x, y)
                val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                histogram[gray]++
            }
        }
        
        return histogram
    }
    
    private fun compareHistograms(hist1: IntArray, hist2: IntArray): Float {
        var intersection = 0
        var union = 0
        
        for (i in hist1.indices) {
            intersection += min(hist1[i], hist2[i])
            union += hist1[i] + hist2[i] - min(hist1[i], hist2[i])
        }
        
        return if (union == 0) 0f else intersection.toFloat() / union.toFloat()
    }
} 