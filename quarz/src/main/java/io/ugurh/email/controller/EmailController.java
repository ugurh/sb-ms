package io.ugurh.monitoring.controller;

import io.ugurh.monitoring.job.EmailJob;
import io.ugurh.monitoring.payload.EmailRequest;
import io.ugurh.monitoring.payload.EmailResponse;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

@RestController
@RequestMapping("/email")
public class EmailController {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailController.class);

    private final Scheduler scheduler;

    public EmailController(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @PostMapping("/send")
    public ResponseEntity<EmailResponse> scheduleEmail(@Valid @RequestBody EmailRequest emailRequest) {

        try {

            ZonedDateTime dateTime = ZonedDateTime.of(emailRequest.getDateTime(), emailRequest.getTimeZone());

            if (dateTime.isBefore(ZonedDateTime.now())) {
                EmailResponse response = EmailResponse.builder()
                        .success(false)
                        .message("dateTime must be after current time")
                        .build();
                return ResponseEntity.badRequest().body(response);
            }

            JobDetail jobDetail = buildJobDetail(emailRequest);
            Trigger trigger = buildJobTrigger(jobDetail, dateTime);
            scheduler.scheduleJob(jobDetail, trigger);

            EmailResponse response = EmailResponse.builder()
                    .success(true)
                    .jobId(jobDetail.getKey().getName())
                    .jobGroup(jobDetail.getKey().getGroup())
                    .message("Email Scheduled Successfully!")
                    .build();

            return ResponseEntity.ok(response);
        } catch (SchedulerException ex) {
            LOGGER.error("Error scheduling email : {0}", ex);
            EmailResponse response = EmailResponse.builder()
                    .success(false)
                    .message("Error scheduling email. Please try later!")
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private JobDetail buildJobDetail(EmailRequest emailRequest) {
        JobDataMap jobDataMap = new JobDataMap();

        jobDataMap.put("email", emailRequest.getEmail());
        jobDataMap.put("subject", emailRequest.getSubject());
        jobDataMap.put("body", emailRequest.getBody());

        return JobBuilder.newJob(EmailJob.class)
                .withIdentity(UUID.randomUUID().toString(), "email-jobs")
                .withDescription("Send Email Job")
                .usingJobData(jobDataMap)
                .storeDurably()
                .build();
    }

    private Trigger buildJobTrigger(JobDetail jobDetail, ZonedDateTime startAt) {
        return TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(jobDetail.getKey().getName(), "email-triggers")
                .withDescription("Send Email Trigger")
                .startAt(Date.from(startAt.toInstant()))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build();
    }

}
