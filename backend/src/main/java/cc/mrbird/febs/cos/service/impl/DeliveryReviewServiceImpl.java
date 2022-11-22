package cc.mrbird.febs.cos.service.impl;

import cc.mrbird.febs.cos.entity.DeliveryReview;
import cc.mrbird.febs.cos.dao.DeliveryReviewMapper;
import cc.mrbird.febs.cos.entity.MessageInfo;
import cc.mrbird.febs.cos.entity.PaymentRecord;
import cc.mrbird.febs.cos.entity.UserInfo;
import cc.mrbird.febs.cos.entity.vo.ContractVo;
import cc.mrbird.febs.cos.entity.vo.DeliveryReviewVo;
import cc.mrbird.febs.cos.service.IDeliveryReviewService;
import cc.mrbird.febs.cos.service.IMessageInfoService;
import cc.mrbird.febs.cos.service.IPaymentRecordService;
import cc.mrbird.febs.cos.service.IUserInfoService;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author FanK
 */
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class DeliveryReviewServiceImpl extends ServiceImpl<DeliveryReviewMapper, DeliveryReview> implements IDeliveryReviewService {

    private final IPaymentRecordService paymentRecordService;

    private final IUserInfoService userInfoService;

    private final IMessageInfoService messageInfoService;

    /**
     * 分页获取交付审核信息
     *
     * @param page           分页对象
     * @param deliveryReview 参数
     * @return 结果
     */
    @Override
    public IPage<LinkedHashMap<String, Object>> selectDeliveryPage(Page<DeliveryReview> page, DeliveryReview deliveryReview) {
        return baseMapper.selectDeliveryPage(page, deliveryReview);
    }

    /**
     * 房租合同预警，租缴费预警
     *
     * @param userCode 用户编号
     * @return 结果
     */
    @Override
    public List<DeliveryReviewVo> selectDeliveryOver(String userCode) {
        // 返回结果
        List<DeliveryReviewVo> result = new ArrayList<>();
        List<MessageInfo> messageInfoList = new ArrayList<>();
        // 所有正常的合同
        List<DeliveryReviewVo> deliveryReviewList = baseMapper.selectDeliveryOver(userCode);
        if (CollectionUtil.isEmpty(deliveryReviewList)) {
            return Collections.emptyList();
        }
        // 查询缴费记录
        List<String> contractList = deliveryReviewList.stream().map(DeliveryReviewVo::getContractCode).distinct().collect(Collectors.toList());
        List<PaymentRecord> paymentRecordList = paymentRecordService.list(Wrappers.<PaymentRecord>lambdaQuery().eq(PaymentRecord::getPaymentType, "1").in(PaymentRecord::getContractCode, contractList));
        // 转MAP
        Map<String, List<PaymentRecord>> paymentRecordMap = paymentRecordList.stream().collect(Collectors.groupingBy(PaymentRecord::getContractCode));
        Map<String, List<DeliveryReviewVo>> deliveryReviewMap = deliveryReviewList.stream().collect(Collectors.groupingBy(DeliveryReviewVo::getRentUserCode));

        for (String rentUserCode : deliveryReviewMap.keySet()) {
            List<DeliveryReviewVo> currentDeliveryList = deliveryReviewMap.get(rentUserCode);
            for (DeliveryReviewVo deliveryReviewVo : currentDeliveryList) {
                boolean isOver = DateUtil.isIn(new Date(), DateUtil.offsetMonth(DateUtil.parseDate(deliveryReviewVo.getEndLive()), -1), DateUtil.parseDate(deliveryReviewVo.getEndLive()));
                deliveryReviewVo.setDeliveryOver(isOver);
                result.add(deliveryReviewVo);
                if (isOver) {
                    MessageInfo messageInfo = new MessageInfo();
                    messageInfo.setDelFlag("0");
                    messageInfo.setToUser(rentUserCode);
                    messageInfo.setTitle(deliveryReviewVo.getHouseAddress() + "合同到期预警");
                    messageInfo.setContent("你好，您所住的【" + deliveryReviewVo.getCommunityName() + "】小区-" + deliveryReviewVo.getHouseAddress() + "合同于" + deliveryReviewVo.getEndLive() + "将到期！如需继续居住请续签合同");
                    messageInfoList.add(messageInfo);

                    MessageInfo ownerMessage = new MessageInfo();
                    ownerMessage.setDelFlag("0");
                    ownerMessage.setToUser(deliveryReviewVo.getOwnerUserCode());
                    ownerMessage.setTitle(deliveryReviewVo.getHouseAddress() + "合同到期预警");
                    ownerMessage.setContent("你好，您所有的【" + deliveryReviewVo.getCommunityName() + "】小区-" + deliveryReviewVo.getHouseAddress() + "合同即将到期！到期时间" + deliveryReviewVo.getEndLive());
                    messageInfoList.add(ownerMessage);
                }
                // 此合同最近一次缴费记录
                List<PaymentRecord> currentPayRecordList = paymentRecordMap.get(deliveryReviewVo.getContractCode());
                currentPayRecordList = currentPayRecordList.stream().sorted(Comparator.comparing(PaymentRecord::getCreateDate)).collect(Collectors.toList());
                PaymentRecord laseRecord = currentPayRecordList.get(currentPayRecordList.size() - 1);
                boolean isRentOver = DateUtil.isIn(new Date(), DateUtil.offsetDay(DateUtil.parseDate(laseRecord.getEndDate()), -15), DateUtil.parseDate(laseRecord.getEndDate()));
                boolean isRentOut = DateUtil.compare(new Date(), DateUtil.parseDate(laseRecord.getEndDate())) > 0;
                deliveryReviewVo.setRentOver(isRentOver || isRentOut);
                if (isRentOver || isRentOut) {
                    MessageInfo messageInfo = new MessageInfo();
                    messageInfo.setDelFlag("0");
                    messageInfo.setToUser(rentUserCode);
                    messageInfo.setTitle(deliveryReviewVo.getHouseAddress() + "缴纳房租提示");
                    messageInfo.setContent("你好，您所住的【" + deliveryReviewVo.getCommunityName() + "】小区-" + deliveryReviewVo.getHouseAddress() + "于" + laseRecord.getEndDate() + "将到期！如需继续居住请缴纳房租");
                    messageInfoList.add(messageInfo);
                }
            }
            messageInfoService.saveBatch(messageInfoList);
        }
        return result;
    }

    /**
     * 添加交付审核信息
     *
     * @param deliveryReview 交付审核信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveDeliveryReview(DeliveryReview deliveryReview) {
        // 默认审核状态
        deliveryReview.setStep("1");
        // 合同状态
        deliveryReview.setContractStatus("1");
        deliveryReview.setCreateDate(DateUtil.formatDate(new Date()));
        // 合同编号
        if (StrUtil.isEmpty(deliveryReview.getContractCode())) {
            deliveryReview.setContractCode("CON-" + System.currentTimeMillis());
        }
        // 计算居住结束时间
        deliveryReview.setEndLive(DateUtil.formatDate(DateUtil.offsetMonth(DateUtil.parse(deliveryReview.getStartLive(), "yyyy-MM-dd"), deliveryReview.getRentDay())));
        // 添加缴费记录
        PaymentRecord deposit = new PaymentRecord();
        deposit.setContractCode(deliveryReview.getContractCode());
        deposit.setCreateDate(deliveryReview.getCreateDate());
        deposit.setRentUserCode(deliveryReview.getRentUserCode());
        deposit.setStartDate(deliveryReview.getCreateDate());
        // 租金缴费记录
        deposit.setAmount(deliveryReview.getContractPrice());
        deposit.setPaymentType("2");
        List<PaymentRecord> paymentRecordList = new ArrayList<>();
        // 房租缴费记录
        PaymentRecord rent = new PaymentRecord();
        rent.setContractCode(deliveryReview.getContractCode());
        rent.setCreateDate(deliveryReview.getCreateDate());
        rent.setRentUserCode(deliveryReview.getRentUserCode());
        rent.setStartDate(deliveryReview.getCreateDate());
        // 租金缴费记录
        rent.setPaymentType("1");
        if ("1".equals(deliveryReview.getPayType())) {
            rent.setRentDay(1);
            rent.setAmount(deliveryReview.getContractPrice());
            rent.setEndDate(DateUtil.formatDate(DateUtil.offsetMonth(new Date(), 1)));
        } else {
            rent.setRentDay(3);
            rent.setAmount(deliveryReview.getContractPrice().multiply(BigDecimal.valueOf(3)));
            rent.setEndDate(DateUtil.formatDate(DateUtil.offsetMonth(new Date(), 3)));
        }
        paymentRecordList.add(deposit);
        paymentRecordList.add(rent);
        paymentRecordService.saveBatch(paymentRecordList);
        return this.save(deliveryReview);
    }

    /**
     * 导出合同信息
     *
     * @param contractCode 合同编号
     * @return 结果
     */
    @Override
    public ContractVo exportContract(String contractCode) {
        return baseMapper.exportContract(contractCode);
    }
}