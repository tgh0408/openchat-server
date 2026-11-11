package com.openchat.user.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
@Data
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class IpDetail implements Serializable {
    private String ip;
    private String isp;
    private String isp_id;
    private String city;
    private String city_id;
    private String country;
    private String country_id;
    private String region;
    private String region_id;
}
