package com.sharkycake.service;

import org.springframework.stereotype.Service;

@Service
public interface PictureDeleteService {


    void deletePicture(Long pictureId,Long operatorId);

}
