package com.tencent.supersonic.headless.server.service.impl;

import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.common.pojo.Constants;
import com.tencent.supersonic.common.pojo.enums.StatusEnum;
import com.tencent.supersonic.common.pojo.enums.TaskStatusEnum;
import com.tencent.supersonic.headless.api.pojo.request.DictItemFilter;
import com.tencent.supersonic.headless.api.pojo.request.DictSingleTaskReq;
import com.tencent.supersonic.headless.api.pojo.response.DictItemResp;
import com.tencent.supersonic.headless.api.pojo.response.DictTaskResp;
import com.tencent.supersonic.headless.core.file.FileHandler;
import com.tencent.supersonic.headless.core.knowledge.helper.HanlpHelper;
import com.tencent.supersonic.headless.server.persistence.dataobject.DictTaskDO;
import com.tencent.supersonic.headless.server.persistence.repository.DictRepository;
import com.tencent.supersonic.headless.server.service.DictTaskService;
import com.tencent.supersonic.headless.server.service.KnowledgeService;
import com.tencent.supersonic.headless.server.utils.DictUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class DictTaskServiceImpl implements DictTaskService {

    @Value("${dict.flush.enable:true}")
    private Boolean dictFlushEnable;
    @Value("${dict.flush.daily.enable:true}")
    private Boolean dictFlushDailyEnable;
    @Value("${dict.file.type:txt}")
    private String dictFileType;
    private String dimValue = "DimValue_%d_%d";

    private final DictRepository dictRepository;
    private final DictUtils dictConverter;
    private final DictUtils dictUtils;
    private final FileHandler fileHandler;
    private final KnowledgeService knowledgeService;

    public DictTaskServiceImpl(DictRepository dictRepository,
                               DictUtils dictConverter,
                               DictUtils dictUtils,
                               FileHandler fileHandler,
                               KnowledgeService knowledgeService) {
        this.dictRepository = dictRepository;
        this.dictConverter = dictConverter;
        this.dictUtils = dictUtils;
        this.fileHandler = fileHandler;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public Long addDictTask(DictSingleTaskReq taskReq, User user) {
        if (!dictFlushEnable) {
            return 0L;
        }
        DictItemResp dictItemResp = fetchDictItemResp(taskReq);
        return handleDictTaskByItemResp(dictItemResp, user);
    }

    private Long handleDictTaskByItemResp(DictItemResp dictItemResp, User user) {
        DictTaskDO dictTaskDO = dictConverter.generateDictTaskDO(dictItemResp, user);
        log.info("[addDictTask] dictTaskDO:{}", dictTaskDO);
        dictRepository.addDictTask(dictTaskDO);
        Long idInDb = dictTaskDO.getId();
        dictItemResp.setId(idInDb);
        runDictTask(dictItemResp, user);
        return idInDb;
    }

    private DictItemResp fetchDictItemResp(DictSingleTaskReq taskReq) {
        DictItemFilter dictItemFilter = DictItemFilter.builder()
                .itemId(taskReq.getItemId())
                .type(taskReq.getType())
                .build();
        List<DictItemResp> dictItemRespList = dictRepository.queryDictConf(dictItemFilter);
        if (!CollectionUtils.isEmpty(dictItemRespList)) {
            return dictItemRespList.get(0);
        }
        return null;
    }

    private void runDictTask(DictItemResp dictItemResp, User user) {
        if (Objects.isNull(dictItemResp)) {
            return;
        }

        DictTaskDO dictTaskDO = dictRepository.queryDictTaskById(dictItemResp.getId());
        dictTaskDO.setStatus(TaskStatusEnum.RUNNING.getStatus());
        dictRepository.editDictTask(dictTaskDO);

        // 1.生成item字典数据
        List<String> data = dictUtils.fetchItemValue(dictItemResp);

        // 2.变更字典文件
        String fileName = dictItemResp.fetchDictFileName() + Constants.DOT + dictFileType;
        fileHandler.writeFile(data, fileName, false);

        // 3.实时变更内存中字典数据
        try {
            HanlpHelper.reloadCustomDictionary();
            dictTaskDO.setStatus(TaskStatusEnum.SUCCESS.getStatus());
            dictRepository.editDictTask(dictTaskDO);
        } catch (IOException e) {
            log.error("reloadCustomDictionary error", e);
        }

    }

    @Override
    public Long deleteDictTask(DictSingleTaskReq taskReq, User user) {
        DictItemResp dictItemResp = fetchDictItemResp(taskReq);
        String fileName = dictItemResp.fetchDictFileName() + Constants.DOT + dictFileType;
        fileHandler.deleteDictFile(fileName);

        try {
            HanlpHelper.reloadCustomDictionary();
        } catch (Exception e) {
            log.error("reloadCustomDictionary error", e);
        }

        return 0L;
    }

    @Override
    @Scheduled(cron = "${knowledge.dimension.value.cron:0 0 0 * * ?}")
    public Boolean dailyDictTask() {
        log.info("[dailyDictTask] start");
        if (!dictFlushDailyEnable) {
            log.info("dictFlushDailyEnable is false, now finish dailyDictTask");
        }
        DictItemFilter filter = DictItemFilter.builder().status(StatusEnum.ONLINE).build();
        List<DictItemResp> dictItemRespList = dictRepository.queryDictConf(filter);
        dictItemRespList.stream().forEach(item -> handleDictTaskByItemResp(item, null));
        log.info("[dailyDictTask] finish");
        return true;
    }

    @Override
    public DictTaskResp queryLatestDictTask(DictSingleTaskReq taskReq, User user) {
        return dictRepository.queryLatestDictTask(taskReq);
    }

}