package org.jeecg.modules.bonus.entity.vo;

import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
//用户登录信息
@Accessors(chain = true)
@ApiModel(value="自定义bonus登录对象", description="激励金Web登录")
public class BonusloginVo {

    private String token;
    private String userName;
}
