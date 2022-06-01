/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.server.master.runner.task;

import org.apache.dolphinscheduler.common.Constants;
import org.apache.dolphinscheduler.common.enums.ExecutionStatus;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.common.utils.LoggerUtils;
import org.apache.dolphinscheduler.dao.entity.ProcessInstance;
import org.apache.dolphinscheduler.dao.entity.TaskInstance;
import org.apache.dolphinscheduler.remote.command.TaskKillRequestCommand;
import org.apache.dolphinscheduler.remote.utils.Host;
import org.apache.dolphinscheduler.server.master.dispatch.context.ExecutionContext;
import org.apache.dolphinscheduler.server.master.dispatch.enums.ExecutorType;
import org.apache.dolphinscheduler.server.master.dispatch.exceptions.ExecuteException;
import org.apache.dolphinscheduler.server.master.dispatch.executor.NettyExecutorManager;
import org.apache.dolphinscheduler.service.bean.SpringApplicationContext;
import org.apache.dolphinscheduler.service.queue.TaskPriority;
import org.apache.dolphinscheduler.service.queue.TaskPriorityQueue;
import org.apache.dolphinscheduler.service.queue.TaskPriorityQueueImpl;
import org.apache.dolphinscheduler.service.queue.entity.TaskExecutionContext;
import org.apache.dolphinscheduler.spi.task.TaskConstants;

import org.apache.commons.lang.StringUtils;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang.time.DateUtils;
import static org.apache.dolphinscheduler.common.Constants.DISCARD_PARAMS;

/**
 * common task processor
 */
public class CommonTaskProcessor extends BaseTaskProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CommonTaskProcessor.class);

    @Autowired
    private TaskPriorityQueue taskUpdateQueue;

    @Autowired
    NettyExecutorManager nettyExecutorManager = SpringApplicationContext.getBean(NettyExecutorManager.class);

    @Override
    public boolean submitTask() {
        this.taskInstance = processService.submitTask(taskInstance, maxRetryTimes, commitInterval);

        if (this.taskInstance == null) {
            return false;
        }
        setTaskExecutionLogger();
        return dispatchTask(taskInstance, processInstance);
    }

    @Override
    public void setTaskExecutionLogger() {
        threadLoggerInfoName = LoggerUtils.buildTaskId(LoggerUtils.TASK_LOGGER_INFO_PREFIX,
                processInstance.getProcessDefinitionCode(),
                processInstance.getProcessDefinitionVersion(),
                taskInstance.getProcessInstanceId(),
                taskInstance.getId());
        Thread.currentThread().setName(String.format(TaskConstants.MASTER_COMMON_TASK_LOGGER_THREAD_NAME_FORMAT, threadLoggerInfoName));
    }

    @Override
    public ExecutionStatus taskState() {
        return this.taskInstance.getState();
    }

    @Override
    public boolean runTask() {
        return true;
    }

    @Override
    protected boolean taskTimeout() {
        return true;
    }

    @Override
    protected boolean persistTask(TaskAction taskAction) {
        switch (taskAction) {
            case STOP:
                if (taskInstance.getState().typeIsFinished() && !taskInstance.getState().typeIsCancel()) {
                    return true;
                }
                taskInstance.setState(ExecutionStatus.KILL);
                taskInstance.setEndTime(new Date());
                processService.updateTaskInstance(taskInstance);
                return true;
            default:
                logger.error("unknown task action: {}", taskAction.toString());

        }
        return false;
    }

    /**
     * common task cannot be paused
     */
    @Override
    protected boolean pauseTask() {
        return true;
    }

    @Override
    public String getType() {
        return Constants.COMMON_TASK_TYPE;
    }

    private boolean dispatchTask(TaskInstance taskInstance, ProcessInstance processInstance) {

        try {
            if (taskUpdateQueue == null) {
                this.initQueue();
            }
            if (taskInstance.getState().typeIsFinished()) {
                logger.info(String.format("submit task , but task [%s] state [%s] is already  finished. ", taskInstance.getName(), taskInstance.getState().toString()));
                return true;
            }
            // task cannot be submitted because its execution state is RUNNING or DELAY.
            if (taskInstance.getState() == ExecutionStatus.RUNNING_EXECUTION
                    || taskInstance.getState() == ExecutionStatus.DELAY_EXECUTION) {
                logger.info("submit task, but the status of the task {} is already running or delayed.", taskInstance.getName());
                return true;
            }
            logger.info("task ready to submit: {}", taskInstance);

            TaskPriority taskPriority = new TaskPriority(processInstance.getProcessInstancePriority().getCode(),
                    processInstance.getId(), taskInstance.getProcessInstancePriority().getCode(),
                    taskInstance.getId(), org.apache.dolphinscheduler.common.Constants.DEFAULT_WORKER_GROUP);

            TaskExecutionContext taskExecutionContext = getTaskExecutionContext(taskInstance);
            taskPriority.setTaskExecutionContext(taskExecutionContext);

            taskUpdateQueue.put(taskPriority);
            logger.info("master submit success, task id:{}, task name:{}, process id:{}",
                    taskInstance.getId(), taskInstance.getName(), taskInstance.getProcessInstanceId());
            //
            JsonNode discardParams = JSONUtils.parseObject(taskExecutionContext.getTaskParams()).findValue(DISCARD_PARAMS);
            if (discardParams != null && !discardParams.isEmpty()) {
                String type = discardParams.get(0).get("type").asText();
                String feature = discardParams.get(0).get("feature").asText();
                Integer value = discardParams.get(0).get("value").asInt();
                long projectCode = taskExecutionContext.getProjectCode();
                long processDefineCode = taskExecutionContext.getProcessDefineCode();
                //  查询 count  t_ds_process_instance.process_definition_code
                List<Integer> statesList = new ArrayList<>();
                statesList.add(7);// 成功
                if (feature.equals("all")){
                    statesList.add(3);// 暂停
                    statesList.add(5);// 停止
                    statesList.add(6);// 失败
                    statesList.add(8);// 需要容错
                    statesList.add(9);// kill
                    statesList.add(13);// 强制成功
                }
                int[] res = statesList.stream().mapToInt(Integer::intValue).toArray();
                List<ProcessInstance> processInstances = processService.queryByProcessDefineCodeAndStatusDiscard(processDefineCode, res);
                List<ProcessInstance> processInstancesNew = new ArrayList<>();
                if (!processInstances.isEmpty()) {
                    if (type.equals("nums")) {
                        if (processInstances.size() > value) {
                            List<ProcessInstance> dbProcessInstance = processInstances.subList(value, processInstances.size());
                            processInstancesNew.addAll(dbProcessInstance);
                        }
                    } else {
                        Date dbStartTime = processInstances.get(0).getStartTime();
                        Date baseDate = DateUtils.addDays(dbStartTime, -value);
                        for (ProcessInstance dbProcessInstance : processInstances) {
                            Date startTime = dbProcessInstance.getStartTime();
                            if (baseDate.after(startTime)) {
                                processInstancesNew.add(dbProcessInstance);
                            }
                        }
                    }
                }
                // 批量删除
                if (!processInstancesNew.isEmpty()) {
                    for (ProcessInstance delProcessInstance : processInstancesNew) {
                        int processInstanceId = delProcessInstance.getId();
                        String strProcessInstanceId = Integer.toString(processInstanceId);
                        try {
                            boolean deleteResult = processService.deleteProcessInstanceByIdNew(projectCode, processInstanceId);
                            if (!deleteResult) {
                                logger.error("discard instances fail: " + strProcessInstanceId);
                            }
                        } catch (Exception e) {
                            logger.error("discard instances Exception: ", e);
                            logger.error("discard instances fail: " + strProcessInstanceId);
                        }
                    }
                }
            }
            //
            return true;
        } catch (Exception e) {
            logger.error("submit task  Exception: ", e);
            logger.error("task error : {}", JSONUtils.toJsonString(taskInstance));
            return false;
        }
    }

    public void initQueue() {
        this.taskUpdateQueue = SpringApplicationContext.getBean(TaskPriorityQueueImpl.class);
    }

    @Override
    public boolean killTask() {

        try {
            taskInstance = processService.findTaskInstanceById(taskInstance.getId());
            if (taskInstance == null) {
                return true;
            }
            if (taskInstance.getState().typeIsFinished()) {
                return true;
            }
            if (StringUtils.isBlank(taskInstance.getHost())) {
                taskInstance.setState(ExecutionStatus.KILL);
                taskInstance.setEndTime(new Date());
                return true;
            }

            TaskKillRequestCommand killCommand = new TaskKillRequestCommand();
            killCommand.setTaskInstanceId(taskInstance.getId());

            ExecutionContext executionContext = new ExecutionContext(killCommand.convert2Command(), ExecutorType.WORKER);

            Host host = Host.of(taskInstance.getHost());
            executionContext.setHost(host);

            nettyExecutorManager.executeDirectly(executionContext);
        } catch (ExecuteException e) {
            logger.error("kill task error:", e);
            return false;
        }

        logger.info("master kill taskInstance name :{} taskInstance id:{}",
                taskInstance.getName(), taskInstance.getId());
        return true;
    }
}
