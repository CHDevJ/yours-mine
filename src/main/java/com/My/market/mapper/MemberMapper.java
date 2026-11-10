package com.My.market.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberMapper {
    public boolean selectId(String username); //중복 Id 검색
}
