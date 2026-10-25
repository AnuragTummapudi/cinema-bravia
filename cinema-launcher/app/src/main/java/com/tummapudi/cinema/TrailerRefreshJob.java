package com.tummapudi.cinema;

import android.app.job.*;
import android.content.*;

/** System binds this short job only when scheduling/network constraints permit. */
public final class TrailerRefreshJob extends JobService {
    private JobParameters running;
    static void schedule(Context context){
        JobScheduler scheduler=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler.getPendingJob(TrailerRepository.JOB_ID)==null)scheduler.schedule(new JobInfo.Builder(TrailerRepository.JOB_ID,new ComponentName(context,TrailerRefreshJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(TrailerRepository.SIX_HOURS,3600000L).setPersisted(true).setBackoffCriteria(1800000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    static void cancel(Context context){((JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(TrailerRepository.JOB_ID);}
    @Override public boolean onStartJob(JobParameters params){running=params;TrailerRepository.get(this).refresh(false,retry->{if(running==params){running=null;jobFinished(params,retry);}});return true;}
    @Override public boolean onStopJob(JobParameters params){running=null;TrailerRepository.get(this).stopJobWork();return true;}
}
