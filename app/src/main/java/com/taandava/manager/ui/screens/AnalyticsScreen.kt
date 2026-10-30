@file:Suppress("FunctionName")

package com.taandava.manager.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taandava.manager.R
import com.taandava.manager.ui.*

@Composable
fun MetricCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun AnalyticsScreen(vm: MainViewModel) {
    val stats by vm.stats.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "Manager Logo",
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
            )
            Text("📊 Database Analytics & Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        // Metrics Grid 1
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(title = "Total Contacts", value = stats.totalContacts.toString(), color = GlassCyan, modifier = Modifier.weight(1f))
            MetricCard(title = "Starred Contacts", value = stats.savedContacts.toString(), color = GlassGold, modifier = Modifier.weight(1f))
        }

        // Metrics Grid 2
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(title = "Duplicate Groups", value = stats.totalDuplicates.toString(), color = GlassAmber, modifier = Modifier.weight(1f))
            MetricCard(title = "SMS Ledger", value = stats.totalSms.toString(), color = GlassPurple, modifier = Modifier.weight(1f))
        }

        // Metrics Grid 3
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(title = "OTP Messages", value = stats.otpCount.toString(), color = GlassIndigo, modifier = Modifier.weight(1f))
            MetricCard(title = "Transactions", value = stats.txnCount.toString(), color = GlassMint, modifier = Modifier.weight(1f))
        }

        // Storage Management Card
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💾 Storage & SQLite Maintenance", fontWeight = FontWeight.Bold, color = TextPrimary)
                val sizeKb = stats.dbSizeBytes / 1024
                Text("Current Database Size: $sizeKb KB", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(
                    "Vacuuming reclaims unused disk pages after deleting duplicate contacts or bulk SMS messages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                GlassPrimaryButton(
                    text = "Vacuum Database",
                    onClick = { vm.vacuumDatabase() }
                )
            }
        }

        // Device Origin Breakdown Card
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📱 Contacts by Device Origin", fontWeight = FontWeight.Bold, color = TextPrimary)
                if (stats.deviceCounts.isEmpty()) {
                    Text("No device provenance records found.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                } else {
                    stats.deviceCounts.forEach { (dev, count) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(dev, color = TextSecondary)
                            GlassBadge(text = "$count contacts", color = GlassCyan, showDot = false)
                        }
                    }
                }
            }
        }

        // About Manager Card
        GlassCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Manager Logo",
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                )
                Column {
                    Text("MANAGER", fontWeight = FontWeight.Black, color = TextPrimary, letterSpacing = 1.sp)
                    Text("Version 4.0 • Jetpack Compose Native", fontSize = 11.sp, color = GlassCyan)
                    Text("Intelligent contact deduplication, SMS ledger & Hostinger 2-way cloud engine.", fontSize = 11.sp, color = TextMuted)
                }
            }
        }
    }
}
