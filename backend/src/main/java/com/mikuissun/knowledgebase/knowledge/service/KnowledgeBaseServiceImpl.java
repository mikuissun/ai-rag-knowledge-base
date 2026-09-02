package com.mikuissun.knowledgebase.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mikuissun.knowledgebase.common.exception.BusinessException;
import com.mikuissun.knowledgebase.knowledge.dto.CreateKnowledgeBaseRequest;
import com.mikuissun.knowledgebase.knowledge.dto.KnowledgeBaseResponse;
import com.mikuissun.knowledgebase.knowledge.dto.UpdateKnowledgeBaseRequest;
import com.mikuissun.knowledgebase.knowledge.entity.KnowledgeBase;
import com.mikuissun.knowledgebase.knowledge.mapper.KnowledgeBaseMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    @Override
    public KnowledgeBaseResponse create(Long userId, CreateKnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setUserId(userId);
        knowledgeBase.setName(request.name().trim());
        knowledgeBase.setDescription(normalizeDescription(request.description()));
        knowledgeBase.setStatus(1);
        knowledgeBaseMapper.insert(knowledgeBase);
        return getByIdAndUserId(knowledgeBase.getId(), userId);
    }

    @Override
    public List<KnowledgeBaseResponse> listByUserId(Long userId) {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getUserId, userId)
                        .orderByDesc(KnowledgeBase::getUpdatedAt))
                .stream()
                .map(KnowledgeBaseResponse::from)
                .toList();
    }

    @Override
    public KnowledgeBaseResponse getByIdAndUserId(Long id, Long userId) {
        return KnowledgeBaseResponse.from(findOwnedKnowledgeBase(id, userId));
    }

    @Override
    public KnowledgeBaseResponse update(Long id, Long userId, UpdateKnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = findOwnedKnowledgeBase(id, userId);
        knowledgeBase.setName(request.name().trim());
        knowledgeBase.setDescription(normalizeDescription(request.description()));
        knowledgeBaseMapper.updateById(knowledgeBase);
        return getByIdAndUserId(id, userId);
    }

    @Override
    public void delete(Long id, Long userId) {
        KnowledgeBase knowledgeBase = findOwnedKnowledgeBase(id, userId);
        knowledgeBaseMapper.deleteById(knowledgeBase.getId());
    }

    private KnowledgeBase findOwnedKnowledgeBase(Long id, Long userId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, id)
                .eq(KnowledgeBase::getUserId, userId));
        if (knowledgeBase == null) {
            throw new BusinessException(404, "知识库不存在");
        }
        return knowledgeBase;
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }
}
