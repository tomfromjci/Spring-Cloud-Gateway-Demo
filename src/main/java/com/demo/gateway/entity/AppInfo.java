package com.demo.gateway.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * 第三方应用信息
 *
 * @author tom
 * @since v
 */
@Data
@TableName("t_app_info")
public class AppInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private String appId;

    private String appSecret;

    private String appName;

    private String appDesc;

    private Byte appStatus;

    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private String createTime;

    @JsonFormat(locale="zh", timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private String updateTime;

}
