package com.cbq.yatori.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {
    Setting setting=new Setting();
    List<User> users;
}
