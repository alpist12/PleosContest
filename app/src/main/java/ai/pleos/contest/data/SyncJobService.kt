package ai.pleos.contest.data

import android.app.job.JobParameters
import android.app.job.JobService

class SyncJobService : JobService() {
    private var active: JobParameters? = null
    override fun onStartJob(params: JobParameters): Boolean {
        active = params
        SyncEngine.once { if (active === params) { active = null; jobFinished(params, false) } }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { active = null; return true }
}
