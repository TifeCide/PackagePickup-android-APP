package cn.aeolusdev.pkgpu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AppScreen(vm)
            }
        }
    }
}

private enum class Tab(val label: String) { HOME("主页"), RECENT("最近取件"), SETTINGS("设置") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(vm: MainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.HOME) }
    val haptic = LocalHapticFeedback.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            tab = it
                        },
                        icon = { Text(if (it == Tab.HOME) "🏠" else if (it == Tab.RECENT) "🕒" else "⚙️") },
                        label = { Text(it.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            Tab.HOME -> HomeTab(modifier = Modifier.padding(padding), data = data, vm = vm)
            Tab.RECENT -> RecentTab(modifier = Modifier.padding(padding), data = data)
            Tab.SETTINGS -> SettingsTab(modifier = Modifier.padding(padding), data = data, vm = vm)
        }
    }
}

@Composable
private fun HomeTab(modifier: Modifier, data: AppData, vm: MainViewModel) {
    val waiting = data.packages.filter { !it.picked }
    val stationOrder = StationSorter.sort(data.stations, waiting, LocalTime.now())
    var addForStation by remember { mutableStateOf<Station?>(null) }
    var pickPrompt by remember { mutableStateOf<PackageEntry?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(stationOrder, key = { it.stationId }) { station ->
                val stationPackages = waiting.filter { it.inStation == station.stationId }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (StationSorter.isOpen(station.openTime)) Color.Unspecified else Color.LightGray.copy(alpha = 0.25f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "${station.alias}（${station.stationId}）", style = MaterialTheme.typography.titleMedium)
                                Text(text = "营业时段：${station.openTime.ifBlank { "未设置" }}")
                                Text(text = "待取包裹：${stationPackages.size}")
                            }
                            FloatingActionButton(onClick = { addForStation = station }) {
                                Text("+")
                            }
                        }
                        if (stationPackages.isEmpty()) {
                            Text("暂无待取包裹")
                        } else {
                            stationPackages.sortedBy { it.addTime }.forEach { pkg ->
                                PackageCard(pkg, showPickedTime = false) {
                                    val sameStationCount = stationPackages.count()
                                    if (sameStationCount > 1) {
                                        pickPrompt = pkg
                                    } else {
                                        vm.markPicked(pkg.packageNo, false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (addForStation != null) {
            AddPackageDialog(
                station = addForStation!!,
                onDismiss = { addForStation = null },
                onConfirm = { tracking, code, memo, company ->
                    vm.addPackage(addForStation!!.stationId, tracking, code, memo, company)
                    addForStation = null
                }
            )
        }

        if (pickPrompt != null) {
            AlertDialog(
                onDismissRequest = { pickPrompt = null },
                title = { Text("同站点多包裹") },
                text = { Text("该站点还有其他包裹，是否全部设为已领取？") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.markPicked(pickPrompt!!.packageNo, true)
                        pickPrompt = null
                    }) { Text("全部领取") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        vm.markPicked(pickPrompt!!.packageNo, false)
                        pickPrompt = null
                    }) { Text("仅当前") }
                }
            )
        }
    }
}

@Composable
private fun RecentTab(modifier: Modifier, data: AppData) {
    val recent = data.packages.filter { it.picked }.sortedByDescending { it.pickedTime ?: 0L }
    LazyColumn(modifier = modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(recent, key = { it.packageNo }) {
            PackageCard(pkg = it, showPickedTime = true, onPick = null)
        }
    }
}

@Composable
private fun PackageCard(pkg: PackageEntry, showPickedTime: Boolean, onPick: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("取件码：${pkg.pickupCode.ifBlank { "暂无取件码，请联系工作人员" }}")
            Text("备注：${pkg.memo.ifBlank { "-" }}")
            Text(TimeUtils.elapsedText(pkg.addTime))
            Text("快递单号：${pkg.trackingNo.ifBlank { "-" }}")
            Text("承运商：${pkg.deliveryCompany}")
            if (showPickedTime) {
                Text("领取时间：${pkg.pickedTime?.let(TimeUtils::formatTime) ?: "-"}")
            }
            if (onPick != null) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = onPick) { Text("标记已领取") }
            }
        }
    }
}

@Composable
private fun AddPackageDialog(
    station: Station,
    onDismiss: () -> Unit,
    onConfirm: (tracking: String, pickupCode: String, memo: String, company: String) -> Unit
) {
    var tracking by remember { mutableStateOf("") }
    var pickupCode by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增包裹 - ${station.alias}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = tracking, onValueChange = { tracking = it }, label = { Text("快递单号(可选)") })
                OutlinedTextField(value = pickupCode, onValueChange = { pickupCode = it }, label = { Text("取件码(可选)") })
                OutlinedTextField(value = memo, onValueChange = { memo = it }, label = { Text("备注") })
                OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("承运商(空则自动识别)") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tracking, pickupCode, memo, company) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SettingsTab(modifier: Modifier, data: AppData, vm: MainViewModel) {
    var editingStation by remember { mutableStateOf<Station?>(null) }
    var addingStation by remember { mutableStateOf(false) }
    var cloud by remember(data.cloudConfig) { mutableStateOf(data.cloudConfig) }

    LaunchedEffect(data.cloudConfig) {
        cloud = data.cloudConfig
    }

    LazyColumn(modifier = modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("站点编辑", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            data.stations.forEach { station ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${station.stationId} - ${station.alias}")
                            Text("营业时间：${station.openTime.ifBlank { "未设置" }}")
                        }
                        TextButton(onClick = { editingStation = station }) { Text("编辑") }
                        TextButton(onClick = { vm.deleteStation(station.stationId) }) { Text("删除") }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Button(onClick = { addingStation = true }) { Text("新增站点") }
        }

        item { Divider() }

        item {
            Text("云同步", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用")
                Spacer(Modifier.width(8.dp))
                Switch(checked = cloud.enabled, onCheckedChange = { cloud = cloud.copy(enabled = it) })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("坚果云预设")
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = cloud.provider == "nutstore",
                    onCheckedChange = {
                        cloud = if (it) {
                            cloud.copy(provider = "nutstore", url = "https://dav.jianguoyun.com/dav/")
                        } else {
                            cloud.copy(provider = "custom")
                        }
                    }
                )
            }
            OutlinedTextField(value = cloud.url, onValueChange = { cloud = cloud.copy(url = it) }, label = { Text("网址") })
            OutlinedTextField(value = cloud.username, onValueChange = { cloud = cloud.copy(username = it) }, label = { Text("账号") })
            OutlinedTextField(
                value = cloud.password,
                onValueChange = { cloud = cloud.copy(password = it) },
                label = { Text("密码") },
                visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(value = cloud.remotePath, onValueChange = { cloud = cloud.copy(remotePath = it) }, label = { Text("云端文件路径") })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveCloudConfig(cloud) }) { Text("保存配置") }
                Button(onClick = {
                    vm.saveCloudConfig(cloud)
                    vm.syncFromCloud()
                }) { Text("立即同步") }
            }
        }
    }

    if (editingStation != null) {
        StationDialog(
            title = "编辑站点",
            initial = editingStation!!,
            onDismiss = { editingStation = null },
            onConfirm = {
                vm.upsertStation(it)
                editingStation = null
            }
        )
    }
    if (addingStation) {
        val nextId = ((data.stations.mapNotNull { it.stationId.toIntOrNull() }.maxOrNull() ?: 0) + 1)
            .toString()
            .padStart(2, '0')
        StationDialog(
            title = "新增站点",
            initial = Station(nextId, "", ""),
            onDismiss = { addingStation = false },
            onConfirm = {
                vm.upsertStation(it)
                addingStation = false
            }
        )
    }
}

@Composable
private fun StationDialog(
    title: String,
    initial: Station,
    onDismiss: () -> Unit,
    onConfirm: (Station) -> Unit
) {
    var stationId by remember { mutableStateOf(initial.stationId) }
    var alias by remember { mutableStateOf(initial.alias) }
    var openTime by remember { mutableStateOf(initial.openTime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = stationId, onValueChange = { stationId = it }, label = { Text("StationID") })
                OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text("Alias") })
                OutlinedTextField(value = openTime, onValueChange = { openTime = it }, label = { Text("OpenTime 例如 08:00-22:00") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (stationId.isNotBlank() && alias.isNotBlank()) {
                    onConfirm(Station(stationId.trim(), alias.trim(), openTime.trim()))
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
