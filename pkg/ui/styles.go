package ui

import (
	"fmt"
	"strings"

	"github.com/charmbracelet/lipgloss"
)

var (
	// Colors
	primaryColor = lipgloss.Color("205") // Pinkish
	infoColor    = lipgloss.Color("39")  // Blue
	successColor = lipgloss.Color("42")  // Green
	errorColor   = lipgloss.Color("160") // Red
	subtleColor  = lipgloss.Color("241") // Grey

	// Styles
	bannerStyle = lipgloss.NewStyle().
			Foreground(primaryColor).
			Bold(true).
			Border(lipgloss.RoundedBorder()).
			Padding(1, 2)

	infoBadge = lipgloss.NewStyle().
			Foreground(lipgloss.Color("230")).
			Background(infoColor).
			Padding(0, 1).
			Bold(true).
			SetString("INFO")

	successBadge = lipgloss.NewStyle().
			Foreground(lipgloss.Color("230")).
			Background(successColor).
			Padding(0, 1).
			Bold(true).
			SetString("SUCCESS")

	errorBadge = lipgloss.NewStyle().
			Foreground(lipgloss.Color("230")).
			Background(errorColor).
			Padding(0, 1).
			Bold(true).
			SetString("ERROR")
	
	textStyle = lipgloss.NewStyle().
			Foreground(lipgloss.Color("252"))
)

// PrintBanner prints the LanDrop banner
func PrintBanner() {
	// NOTE: Backticks inside the ASCII art have been replaced with apostrophes (') 
	// to prevent syntax errors in the Go string literal.
	banner := `
        ,gggg,                          ,gggggggggggg,                       
       d8" "8I                         dP"""88""""""Y8b,                     
       88  ,dP                         Yb,  88       '8b,                    
    8888888P"                           '"  88        '8b                    
       88                                   88         Y8                    
       88           ,gggg,gg   ,ggg,,ggg,   88         d8 ,gggggg,    ,ggggg,    gg,gggg,   
  ,aa,_88          dP"  "Y8I  ,8" "8P" "8,  88        ,8P dP""""8I   dP"  "Y8gggI8P"  "Yb  
 dP" "88P         i8'    ,8I  I8   8I   8I  88       ,8P',8'    8I  i8'    ,8I  I8'    ,8i 
 Yb,_,d88b,,_    ,d8,    ,d8b,,dP   8I   Yb, 88______,dP',dP      Y8,,d8,    ,d8' ,I8 _  ,d8' 
  "Y8P"  "Y88888P"Y8888P"'Y88P'    8I   'Y8888888888P"  8P       'Y8P"Y8888P"    PI8 YY88888P
                                                                                 I8          
                                                                                 I8          
                                                                                 I8          
                                                                                 I8          
                                                                                 I8          
                                                                                 I8          
`
	fmt.Println(bannerStyle.Render(strings.TrimSpace(banner)))
	fmt.Println()
}

// Info prints an info message
func Info(format string, a ...interface{}) {
	msg := fmt.Sprintf(format, a...)
	fmt.Printf("%s %s\n", infoBadge.String(), textStyle.Render(msg))
}

// Success prints a success message
func Success(format string, a ...interface{}) {
	msg := fmt.Sprintf(format, a...)
	fmt.Printf("%s %s\n", successBadge.String(), textStyle.Render(msg))
}

// Error prints an error message
func Error(format string, a ...interface{}) {
	msg := fmt.Sprintf(format, a...)
	fmt.Printf("%s %s\n", errorBadge.String(), textStyle.Render(msg))
}

// Render returns a generic string using the text style
func Render(s string) string {
	return textStyle.Render(s)
}
