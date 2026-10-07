package com.ggaming.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Si las fotos superan el límite de tamaño, Spring corta el pedido antes de
 * llegar al controlador. Acá lo atrapamos y mostramos un mensaje entendible.
 */
@ControllerAdvice
public class ErroresSubida {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String demasiadoGrande(RedirectAttributes flash) {
        flash.addFlashAttribute("error", "Las fotos pesan demasiado (máximo 15 MB cada una y 80 MB en total).");
        return "redirect:/admin";
    }
}
