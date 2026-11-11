import { z } from 'zod'

/** Backend sınırları: e-posta en fazla 255; şifre 10–128, en az 4 farklı karakter (PasswordPolicy). */
export const PASSWORD_MIN = 10
export const PASSWORD_MAX = 128

export const emailField = z
  .string()
  .trim()
  .min(1, 'E-posta adresinizi girin')
  .max(255, 'E-posta en fazla 255 karakter olabilir')
  .email('Geçerli bir e-posta adresi girin')

export const loginSchema = z.object({
  email: emailField,
  password: z.string().min(1, 'Şifrenizi girin'),
})
export type LoginValues = z.infer<typeof loginSchema>

export const forgotSchema = z.object({ email: emailField })
export type ForgotValues = z.infer<typeof forgotSchema>

export const newPasswordField = z
  .string()
  .min(PASSWORD_MIN, `Şifre en az ${PASSWORD_MIN} karakter olmalı`)
  .max(PASSWORD_MAX, `Şifre en fazla ${PASSWORD_MAX} karakter olabilir`)
  .refine((v) => new Set(v).size >= 4, 'Şifre çok basit. Farklı karakterler kullanın')

export const resetSchema = z
  .object({
    newPassword: newPasswordField,
    confirmPassword: z.string().min(1, 'Şifreyi tekrar girin'),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Şifreler eşleşmiyor',
  })
export type ResetValues = z.infer<typeof resetSchema>
