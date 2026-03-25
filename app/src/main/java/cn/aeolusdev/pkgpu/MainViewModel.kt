package cn.aeolusdev.pkgpu

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = Storage(application)
    private val syncClient = WebDavSyncClient()

    private val _data = MutableStateFlow(storage.load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    init {
        syncFromCloud()
    }

    fun syncFromCloud() {
        viewModelScope.launch {
            val synced = withContext(Dispatchers.IO) {
                syncClient.sync(_data.value, _data.value.cloudConfig)
            }
            _data.value = synced
            withContext(Dispatchers.IO) {
                storage.save(synced)
            }
        }
    }

    fun saveCloudConfig(config: CloudConfig) {
        _data.value = _data.value.copy(cloudConfig = config, updatedAt = System.currentTimeMillis())
        persist()
    }

    fun upsertStation(station: Station) {
        val stations = _data.value.stations.toMutableList()
        val index = stations.indexOfFirst { it.stationId == station.stationId }
        if (index >= 0) stations[index] = station else stations.add(station)
        _data.value = _data.value.copy(stations = stations, updatedAt = System.currentTimeMillis())
        persist()
    }

    fun deleteStation(stationId: String) {
        _data.value = _data.value.copy(
            stations = _data.value.stations.filterNot { it.stationId == stationId },
            updatedAt = System.currentTimeMillis()
        )
        persist()
    }

    fun addPackage(
        stationId: String,
        trackingNo: String,
        pickupCode: String,
        memo: String,
        manualCompany: String
    ) {
        val nextNo = (_data.value.packages.maxOfOrNull { it.packageNo } ?: 0) + 1
        val company = if (manualCompany.isNotBlank()) manualCompany else CarrierRecognizer.recognize(trackingNo)
        val entry = PackageEntry(
            packageNo = nextNo,
            trackingNo = trackingNo,
            inStation = stationId,
            pickupCode = pickupCode,
            deliveryCompany = company,
            memo = memo,
            addTime = System.currentTimeMillis()
        )
        _data.value = _data.value.copy(packages = _data.value.packages + entry, updatedAt = System.currentTimeMillis())
        persist()
    }

    fun markPicked(packageNo: Int, pickAllInStation: Boolean) {
        val source = _data.value.packages
        val target = source.firstOrNull { it.packageNo == packageNo } ?: return
        val now = System.currentTimeMillis()
        val updated = source.map {
            if (!it.picked && (it.packageNo == packageNo || (pickAllInStation && it.inStation == target.inStation))) {
                it.copy(picked = true, pickedTime = now)
            } else it
        }
        _data.value = _data.value.copy(packages = updated, updatedAt = now)
        persist()
    }

    private fun persist() {
        viewModelScope.launch(Dispatchers.IO) {
            storage.save(_data.value)
        }
    }
}
