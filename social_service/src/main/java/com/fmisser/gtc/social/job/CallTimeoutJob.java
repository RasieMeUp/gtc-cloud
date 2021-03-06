package com.fmisser.gtc.social.job;

import com.fmisser.gtc.social.service.ActiveService;
import com.fmisser.gtc.social.service.ImService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author fmisser
 * @create 2021-04-13 下午6:33
 * @description 通话邀请超时定时任务
 */
@Component
public class CallTimeoutJob implements Job {

    @Autowired
    private ImService imService;

    @Autowired
    private ActiveService activeService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobKey jobKey = context.getJobDetail().getKey();
        Long roomId = Long.parseLong(jobKey.getName());
        imService.timeoutGen(roomId);
    }
}
