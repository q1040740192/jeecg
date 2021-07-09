package org.jeecg.modules.bonus.entity.vo;


import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
//用户登录信息
@Accessors(chain = true)
@ApiModel(value="bonus分页查询对象", description="激励金专属分页")
public class BonusPageVO {

    @ApiModelProperty(value = "当前页" ,required = true)
    private Integer pageNo;

    @ApiModelProperty(value = "总页数",required = true)
    private Integer pageSize;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty(value = "开始时间 --格式yyyy-MM-dd")
    private Date uptime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty(value = "结束时间 --格式yyyy-MM-dd")
    private Date endtime;

    @ApiModelProperty(value = "文件名称",example="小米")
    private String filename;
}
