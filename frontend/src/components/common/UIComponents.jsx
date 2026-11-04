import React from 'react';

export function Button({
                           children,
                           variant = 'primary',
                           size = 'md',
                           onClick,
                           disabled = false,
                           icon,
                           className = '',
                           ...props
                       }) {
    return (
        <button
            onClick={onClick}
            disabled={disabled}
            className={`btn btn-${variant} btn-${size} ${className}`}
            {...props}
        >
            {icon && <span>{icon}</span>}
            {children}
        </button>
    );
}

export function Input({
                          label,
                          icon,
                          error,
                          value,
                          onChange,
                          placeholder,
                          type = 'text',
                          className = '',
                          ...props
                      }) {
    return (
        <div className={`input-group ${className}`}>
            {label && <label className="input-label">{label}</label>}
            <div className={`input-container ${error ? 'error' : ''}`}>
                {icon && <span className="input-icon">{icon}</span>}
                <input
                    type={type}
                    value={value}
                    onChange={onChange}
                    placeholder={placeholder}
                    className="input-field"
                    {...props}
                />
            </div>
            {error && <span className="input-error-msg">{error}</span>}
        </div>
    );
}

export function Badge({ children, variant = 'neutral', className = '', ...props }) {
    return (
        <span className={`badge badge-${variant} ${className}`} {...props}>
      {children}
    </span>
    );
}