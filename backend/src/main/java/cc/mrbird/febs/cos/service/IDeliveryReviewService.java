package cc.mrbird.febs.cos.service;

import cc.mrbird.febs.cos.entity.DeliveryReview;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.apache.ibatis.annotations.Param;

import java.util.LinkedHashMap;

/**
 * @author FanK
 */
public interface IDeliveryReviewService extends IService<DeliveryReview> {

    /**
     * 分页获取交付审核信息
     *
     * @param page           分页对象
     * @param deliveryReview 参数
     * @return 结果
     */
    IPage<LinkedHashMap<String, Object>> selectDeliveryPage(Page<DeliveryReview> page, DeliveryReview deliveryReview);

    /**
     * 添加交付审核信息
     *
     * @param deliveryReview 交付审核信息
     * @return 结果
     */
    boolean saveDeliveryReview(DeliveryReview deliveryReview);
}
