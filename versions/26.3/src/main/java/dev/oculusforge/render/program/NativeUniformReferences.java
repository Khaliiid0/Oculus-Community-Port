package dev.oculusforge.render.program;

import java.util.ArrayDeque;
import java.util.regex.Pattern;

/** Bounded lexical scope handling for native uniform references in comment-free GLSL. */
final class NativeUniformReferences {
    private static final String TYPE="(?:bool|int|uint|float|double|[biud]?vec[234]|d?mat[234](?:x[234])?)";
    private static final Pattern TOKEN=Pattern.compile("[A-Za-z_]\\w*|[^\\s]");
    private NativeUniformReferences() {}
    static String replace(String source,String name,String replacement) throws ShaderCompatibilityException {
        return replace(source,name,replacement,false);
    }
    /** Bounded opt-in for scalar float helper parameters (BSL GetPuddles). */
    static String replace(String source,String name,String replacement,boolean floatParameters) throws ShaderCompatibilityException {
        var scopes=new ArrayDeque<Boolean>();
        scopes.push(false);
        int parentheses=0;
        int declarationParentheses=-1;
        boolean nextDeclarator=false;
        String previous="", beforePrevious="";
        boolean signature=false, parameterShadow=false, pendingFunctionShadow=false;
        var tokens=TOKEN.matcher(source);
        var result=new StringBuilder();
        while(tokens.find()) {
            String token=tokens.group();
            boolean identifier=token.matches("[A-Za-z_]\\w*");
            boolean typedDeclaration=identifier && previous.matches(TYPE);
            boolean listDeclaration=identifier && nextDeclarator;
            if(typedDeclaration)declarationParentheses=parentheses;
            if(identifier)nextDeclarator=false;
            if(token.equals(";") || token.equals("{") || token.equals("}")) {
                declarationParentheses=-1;nextDeclarator=false;
            }
            if(token.equals(",") && declarationParentheses==parentheses)nextDeclarator=true;
            if(token.equals("{")) {
                scopes.push(scopes.peek() || pendingFunctionShadow);
                pendingFunctionShadow=false;
            }
            else if(token.equals("}")) {
                if(scopes.size()==1)throw new ShaderCompatibilityException("Unbalanced native shader scope");
                scopes.pop();
            } else if(token.equals("(")) {
                if(scopes.size()==1 && parentheses==0 && previous.matches("[A-Za-z_]\\w*")
                    && (beforePrevious.matches(TYPE)||beforePrevious.equals("void"))) {
                    signature=true;parameterShadow=false;
                }
                parentheses++;
            } else if(token.equals(")")) {
                parentheses--;
                if(signature && parentheses==0) {
                    pendingFunctionShadow=parameterShadow;
                    signature=false;parameterShadow=false;
                }
            }
            if(token.equals(";"))pendingFunctionShadow=false; // Prototype, not the next function's body.
            if(token.equals(name) && !previous.equals(".")) {
                if(listDeclaration)
                    throw new ShaderCompatibilityException("Unsupported native uniform shadowing declaration list: "+name);
                if(typedDeclaration) {
                    if(floatParameters && scopes.size()==1 && parentheses==1 && signature
                        && previous.equals("float")) {
                        String after=source.substring(tokens.end()).stripLeading();
                        if(!after.startsWith(",") && !after.startsWith(")"))
                            throw new ShaderCompatibilityException("Unsupported native float uniform parameter: "+name);
                        parameterShadow=true;
                    } else if(scopes.size()==1 || parentheses!=0) {
                        throw new ShaderCompatibilityException("Unsupported native uniform shadowing parameter/loop declaration: "+name);
                    } else {
                        scopes.pop();scopes.push(true);
                    }
                } else if(!scopes.peek() && !(signature && parameterShadow)) {
                    tokens.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(replacement));
                }
            }
            beforePrevious=previous;previous=token;
        }
        tokens.appendTail(result);
        return result.toString();
    }
}
