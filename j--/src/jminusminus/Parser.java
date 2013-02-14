// Copyright 2013 Bill Campbell, Swami Iyer and Bahar Akbal-Delibas

package jminusminus;

import java.util.ArrayList;
import static jminusminus.TokenKind.*;

/**
 * A recursive descent parser that, given a lexical analyzer (a
 * LookaheadScanner), parses a Java compilation unit (program file), taking
 * tokens from the LookaheadScanner, and produces an abstract syntax tree (AST)
 * for it.
 */

public class Parser {

	/** The lexical analyzer with which tokens are scanned. */
	private LookaheadScanner scanner;

	/** Whether a parser error has been found. */
	private boolean isInError;

	/** Whether we have recovered from a parser error. */
	private boolean isRecovered;

	/**
	 * Construct a parser from the given lexical analyzer.
	 * 
	 * @param scanner
	 *            the lexical analyzer with which tokens are scanned.
	 */

	public Parser(LookaheadScanner scann) {
		this.scanner = scann;
		this.isInError = false;
		this.isRecovered = true;
		scanner.next(); // Prime the pump
	}

	/**
	 * Has a parser error occurred up to now?
	 * 
	 * @return true or false.
	 */

	public boolean errorHasOccurred() {
		return this.isInError;
	}

	// ////////////////////////////////////////////////
	// Parsing Support ///////////////////////////////
	// ////////////////////////////////////////////////

	/**
	 * Is the current token this one?
	 * 
	 * @param sought
	 *            the token we're looking for.
	 * @return true iff they match; false otherwise.
	 */

	private boolean see(TokenKind sought) {
		return sought == this.scanner.token().kind();
	}

	/**
	 * Look at the current (unscanned) token to see if it's one we're 
	 * looking
	 * for. If so, scan it and return true; otherwise return false (without
	 * scanning a thing).
	 * 
	 * @param sought
	 *            the token we're looking for.
	 * @return true iff they match; false otherwise.
	 */

	private boolean have(TokenKind sought) {
		if (this.see(sought)) {
			this.scanner.next();
			return true;
		}
		return false;
	}

	/**
	 * Attempt to match a token we're looking for with the current input 
	 * token.
	 * If we succeed, scan the token and go into a "isRecovered" state. 
	 * If we fail, then what we do next depends on whether or not we're
	 *  currently in a "isRecovered" state: if so, we report the error and 
	 *  go into an "Unrecovered" state; if not, we repeatedly scan tokens 
	 *  until we find the one we're looking for (or EOF) and then return to
	 *  a "isRecovered" state.
	 * This gives us a kind of poor man's syntactic error recovery. 
	 * The strategy is due to David Turner and Ron Morrison.
	 * 
	 * @param sought
	 *            the token we're looking for.
	 */

	private void mustBe(TokenKind sought) {
		if (this.scanner.token().kind() == sought) {
			this.scanner.next();
			this.isRecovered = true;
		} else if (this.isRecovered) {
			this.isRecovered = false;
			this.reportParserError("%s found where %s sought", 
				this.scanner.token().image(), sought.image());
		} else {
			// Do not report the (possibly spurious) error,
			// but rather attempt to recover by forcing a match.
			while (!this.see(sought) && !this.see(EOF)) {
				this.scanner.next();
			}
			if (this.see(sought)) {
				this.scanner.next();
				this.isRecovered = true;
			}
		}
	}

	/**
	 * Pull out the ambiguous part of a name and return it.
	 * 
	 * @param name
	 *            with an ambiguos part (possibly).
	 * @return ambiguous part or null.
	 */

	private AmbiguousName ambiguousPart(TypeName name) {
		final String qualifiedName = name.toString();
		final int lastDotIndex = qualifiedName.lastIndexOf('.');
		return lastDotIndex == -1 ? null // It was a simple
				// name
			: new AmbiguousName(name.line(), qualifiedName
					.substring(0, lastDotIndex));
	}

	/**
	 * Report a syntax error.
	 * 
	 * @param message
	 *            message identifying the error.
	 * @param args
	 *            related values.
	 */

	private void reportParserError(String message, Object... args) {
		this.isInError = true;
		this.isRecovered = false;
		System.err.printf("%s:%d: ", this.scanner.fileName(), 
				this.scanner.token().line());
		System.err.printf(message, args);
		System.err.println();
	}

	// ////////////////////////////////////////////////
	// Lookahead /////////////////////////////////////
	// ////////////////////////////////////////////////

	/**
	 * Are we looking at an IDENTIFIER followed by a LPAREN? Look ahead 
	 * to find out.
	 * 
	 * @return true iff we're looking at IDENTIFIER LPAREN; false otherwise.
	 */

	private boolean seeIdentLParen() {
		this.scanner.recordPosition();
		final boolean result = this.have(IDENTIFIER) 
				&& this.see(LPAREN);
		this.scanner.returnToPosition();
		return result;
	}

	/**
	 * Are we looking at a cast? ie.
	 * 
	 * <pre>
	 *   LPAREN type RPAREN ...
	 * </pre>
	 * 
	 * Look ahead to find out.
	 * 
	 * @return true iff we're looking at a cast; false otherwise.
	 */

	private boolean seeCast() {
		this.scanner.recordPosition();
		if (!this.have(LPAREN)) {
			this.scanner.returnToPosition();
			return false;
		}
		if (seeBasicType()) {
			this.scanner.returnToPosition();
			return true;
		}
		if (!this.see(IDENTIFIER)) {
			this.scanner.returnToPosition();
			return false;
		}
		this.scanner.next(); // Scan the IDENTIFIER
		// A qualified identifier is ok
		while (this.have(DOT)) {
			if (!this.have(IDENTIFIER)) {
				this.scanner.returnToPosition();
				return false;
			}
		}
		while (this.have(LBRACK)) {
			if (!this.have(RBRACK)) {
				this.scanner.returnToPosition();
				return false;
			}
		}
		if (!this.have(RPAREN)) {
			this.scanner.returnToPosition();
			return false;
		}
		this.scanner.returnToPosition();
		return true;
	}

	/**
	 * Are we looking at a local variable declaration? ie.
	 * 
	 * <pre>
	 *   type IDENTIFIER {LBRACK RBRACK} ...
	 * </pre>
	 * 
	 * Look ahead to determine.
	 * 
	 * @return true iff we are looking at local variable declaration; false
	 *         otherwise.
	 */

	private boolean seeLocalVariableDeclaration() {
		this.scanner.recordPosition();
		if (this.have(IDENTIFIER)) {
			// A qualified identifier is ok
			while (this.have(DOT)) {
				if (!this.have(IDENTIFIER)) {
					this.scanner.returnToPosition();
					return false;
				}
			}
		} else if (seeBasicType()) {
			this.scanner.next();
		} else {
			this.scanner.returnToPosition();
			return false;
		}
		while (this.have(LBRACK)) {
			if (!this.have(RBRACK)) {
				this.scanner.returnToPosition();
				return false;
			}
		}
		if (!this.have(IDENTIFIER)) {
			this.scanner.returnToPosition();
			return false;
		}
		while (this.have(LBRACK)) {
			if (!this.have(RBRACK)) {
				this.scanner.returnToPosition();
				return false;
			}
		}
		this.scanner.returnToPosition();
		return true;
	}

	/**
	 * Are we looking at a basic type? ie.
	 * 
	 * <pre>
	 * BOOLEAN | CHAR | INT
	 * </pre>
	 * 
	 * @return true iff we're looking at a basic type; false otherwise.
	 */

	private boolean seeBasicType() {
		if (this.see(BOOLEAN) || this.see(CHAR) || this.see(INT)) {
			return true;
		}
		return false;
	}

	/**
	 * Are we looking at a reference type? ie.
	 * 
	 * <pre>
	 *   referenceType ::= basicType LBRACK RBRACK {LBRACK RBRACK}
	 *                   | qualifiedIdentifier {LBRACK RBRACK}
	 * </pre>
	 * 
	 * @return true iff we're looking at a reference type; false otherwise.
	 */

	private boolean seeReferenceType() {
		if (this.see(IDENTIFIER)) {
			return true;
		}
		this.scanner.recordPosition();
		if (this.have(BOOLEAN) || this.have(CHAR) || this.have(INT)) {
			if (this.have(LBRACK) && this.see(RBRACK)) {
				this.scanner.returnToPosition();
				return true;
			}
		}
		this.scanner.returnToPosition();
		return false;
	}

	/**
	 * Are we looking at []?
	 * 
	 * @return true iff we're looking at a [] pair; false otherwise.
	 */

	private boolean seeDims() {
		this.scanner.recordPosition();
		final boolean result = this.have(LBRACK) && this.see(RBRACK);
		this.scanner.returnToPosition();
		return result;
	}

	// ////////////////////////////////////////////////
	// Parser Proper /////////////////////////////////
	// ////////////////////////////////////////////////

	/**
	 * Parse a compilation unit (a program file) and construct an AST 
	 * for it.
	 * After constructing the Parser, this is its entry point.
	 * 
	 * <pre>
	 *   compilationUnit ::= [PACKAGE qualifiedIdentifier SEMI]
	 *                       {IMPORT  qualifiedIdentifier SEMI}
	 *                       {typeDeclaration}
	 *                       EOF
	 * </pre>
	 * 
	 * @return an AST for a compilationUnit.
	 */

	public JCompilationUnit compilationUnit() {
		final int line = this.scanner.token().line();
		TypeName packageName = null; // Default
		if (this.have(PACKAGE)) {
			packageName = qualifiedIdentifier();
			this.mustBe(SEMI);
		}
		final ArrayList<TypeName> imports = new ArrayList<TypeName>();
		while (this.have(IMPORT)) {
			imports.add(qualifiedIdentifier());
			this.mustBe(SEMI);
		}
		final ArrayList<JAST> typeDeclarations = new ArrayList<JAST>();
		while (!this.see(EOF)) {
			final JAST typeDeclaration = typeDeclaration();
			if (typeDeclaration != null) {
				typeDeclarations.add(typeDeclaration);
			}
		}
		this.mustBe(EOF);
		return new JCompilationUnit(this.scanner.fileName(),
				line, packageName, imports, typeDeclarations);
	}

	/**
	 * Parse a qualified identifier.
	 * 
	 * <pre>
	 *   qualifiedIdentifier ::= IDENTIFIER {DOT IDENTIFIER}
	 * </pre>
	 * 
	 * @return an instance of TypeName.
	 */

	private TypeName qualifiedIdentifier() {
		final int line = this.scanner.token().line();
		this.mustBe(IDENTIFIER);
		String qualifiedIdentifier = this.scanner.
				previousToken().image();
		while (this.have(DOT)) {
			this.mustBe(IDENTIFIER);
			qualifiedIdentifier += "." + this.scanner
					.previousToken()
					.image();
		}
		return new TypeName(line, qualifiedIdentifier);
	}

	/**
	 * Parse a type declaration.
	 * 
	 * <pre>
	 *   typeDeclaration ::= modifiers classDeclaration
	 * </pre>
	 * 
	 * @return an AST for a typeDeclaration.
	 */

	private JAST typeDeclaration() {
		final ArrayList<String> mods = this.modifiers();
		return classDeclaration(mods);
	}

	/**
	 * Parse modifiers.
	 * 
	 * <pre>
	 *   modifiers ::= {PUBLIC | PROTECTED | PRIVATE | STATIC | 
	 *                  ABSTRACT}
	 * </pre>
	 * 
	 * Check for duplicates, and conflicts among access modifiers (public,
	 * protected, and private). Otherwise, no checks.
	 * 
	 * @return a list of modifiers.
	 */

	private ArrayList<String> modifiers() {
		final ArrayList<String> mods = new ArrayList<String>();
		boolean scannedPUBLIC = false;
		boolean scannedPROTECTED = false;
		boolean scannedPRIVATE = false;
		boolean scannedSTATIC = false;
		boolean scannedABSTRACT = false;
		boolean more = true;
		while (more)
			if (this.have(PUBLIC)) {
				mods.add("public");
				if (scannedPUBLIC) {
					this.reportParserError(
						"Repeated modifier:public");
				}
				if (scannedPROTECTED || scannedPRIVATE) {
					this.reportParserError(
						"Access conflict in modifiers");
				}
				scannedPUBLIC = true;
			} else if (this.have(PROTECTED)) {
				mods.add("protected");
				if (scannedPROTECTED) {
					this.reportParserError(
						"Repeated modifier: protected");
				}
				if (scannedPUBLIC || scannedPRIVATE) {
					this.reportParserError(
						"Access conflict in modifiers");
				}
				scannedPROTECTED = true;
			} else if (this.have(PRIVATE)) {
				mods.add("private");
				if (scannedPRIVATE) {
					this.reportParserError(
						"Repeated modifier: private");
				}
				if (scannedPUBLIC || scannedPROTECTED) {
					this.reportParserError(
						"Access conflict in modifiers");
				}
				scannedPRIVATE = true;
			} else if (this.have(STATIC)) {
				mods.add("static");
				if (scannedSTATIC) {
					this.reportParserError(
						"Repeated modifier: static");
				}
				scannedSTATIC = true;
			} else if (this.have(ABSTRACT)) {
				mods.add("abstract");
				if (scannedABSTRACT) {
					this.reportParserError(
						"Repeated modifier: abstract");
				}
				scannedABSTRACT = true;
			} else {
				more = false;
			}
		return mods;
	}
 
	/**
	 * Parse a class declaration.
	 * 
	 * <pre>
	 *   classDeclaration ::= CLASS IDENTIFIER 
	 *                        [EXTENDS qualifiedIdentifier] 
	 *                        classBody
	 * </pre>
	 * 
	 * A class which doesn't explicitly extend another
	 *  (super) class implicitly
	 * extends the superclass java.lang.Object.
	 * 
	 * @param mods
	 *            the class modifiers.
	 * @return an AST for a classDeclaration.
	 */

	private JClassDeclaration classDeclaration(
			ArrayList<String> mods) {
		final int line = this.scanner.token().line();
		this.mustBe(CLASS);
		this.mustBe(IDENTIFIER);
		final String name = this.scanner.previousToken().image();
		Type superClass;
		if (this.have(EXTENDS)) {
			superClass = this.qualifiedIdentifier();
		} else {
			superClass = Type.OBJECT;
		}
		return new JClassDeclaration(line, mods, name,
				superClass, classBody());
	}

	/**
	 * Parse a class body.
	 * 
	 * <pre>
	 *   classBody ::= LCURLY
	 *                   {modifiers memberDecl}
	 *                 RCURLY
	 * </pre>
	 * 
	 * @return list of members in the class body.
	 */

	private ArrayList<JMember> classBody() {
		final ArrayList<JMember> members = new ArrayList<JMember>();
		this.mustBe(LCURLY);
		while (!this.see(RCURLY) && !this.see(EOF)) {
			members.add(memberDecl(this.modifiers()));
		}
		this.mustBe(RCURLY);
		return members;
	}

	/**
	 * Parse a member declaration.
	 * 
	 * <pre>
	 *   memberDecl ::= IDENTIFIER            // constructor
	 *                    formalParameters
	 *                    block
	 *                | (VOID | type) IDENTIFIER  // method
	 *                    formalParameters
	 *                    (block | SEMI)
	 *                | type variableDeclarators SEMI
	 * </pre>
	 * 
	 * @param mods
	 *            the class member modifiers.
	 * @return an AST for a memberDecl.
	 */

	private JMember memberDecl(ArrayList<String> mods) {
		final int line = this.scanner.token().line();
		JMember memberDecl = null;
		if (this.seeIdentLParen()) {
			// A constructor
			this.mustBe(IDENTIFIER);
			final String name = this.scanner
					.previousToken().image();
			final ArrayList<JFormalParameter> params 
			= formalParameters();
			final JBlock body = this.block();
			memberDecl = new JConstructorDeclaration(
					line, mods, name, params, body);
		} else {
			Type type = null;
			if (this.have(VOID)) {
				// void method
				type = Type.VOID;
				this.mustBe(IDENTIFIER);
				final String name = this.scanner
						.previousToken().image();
				final ArrayList<JFormalParameter> params 
				= this.formalParameters();
				final JBlock body = this.have(SEMI)
						? null : block();
				memberDecl = new JMethodDeclaration(
					line, mods, name, type, params, body);
			} else {
				type = type();
				if (this.seeIdentLParen()) {
					// Non void method
					this.mustBe(IDENTIFIER);
					final String name = this.scanner
						.previousToken().image();
					final ArrayList<JFormalParameter> 
					params = formalParameters();
					final JBlock body = this.have(SEMI) 
						? null : this.block();
					memberDecl = new JMethodDeclaration(
					line, mods, name, type,	params, body);
				} else {
					// Field
					memberDecl = new JFieldDeclaration(
						line, mods,	
						this.variableDeclarators(type));
					this.mustBe(SEMI);
				}
			}
		}
		return memberDecl;
	}

	/**
	 * Parse a block.
	 * 
	 * <pre>
	 *   block ::= LCURLY {blockStatement} RCURLY
	 * </pre>
	 * 
	 * @return an AST for a block.
	 */

	private JBlock block() {
		final int line = this.scanner.token().line();
		final ArrayList<JStatement> statements = 
				new ArrayList<JStatement>();
		this.mustBe(LCURLY);
		while (!this.see(RCURLY) && !this.see(EOF)) {
			statements.add(blockStatement());
		}
		this.mustBe(RCURLY);
		return new JBlock(line, statements);
	}

	/**
	 * Parse a block statement.
	 * 
	 * <pre>
	 *   blockStatement ::= localVariableDeclarationStatement
	 *                    | statement
	 * </pre>
	 * 
	 * @return an AST for a blockStatement.
	 */

	private JStatement blockStatement() {
		if (this.seeLocalVariableDeclaration()) {
			return localVariableDeclarationStatement();
		}
		return statement();

	}

	/**
	 * Parse a statement.
	 * 
	 * <pre>
	 *   statement ::= block
	 *               | IF parExpression statement [ELSE statement]
	 *               | WHILE parExpression statement 
	 *               | RETURN [expression] SEMI
	 *               | SEMI 
	 *               | statementExpression SEMI
	 * </pre>
	 * 
	 * @return an AST for a statement.
	 */

	private JStatement statement() {
		final int line = this.scanner.token().line();
		if (this.see(LCURLY)) {
			return this.block();
		} else if (this.have(IF)) {
			final JExpression test = this.parExpression();
			final JStatement consequent = this.statement();
			final JStatement alternate = this.have(ELSE)
					? this.statement() : null;
			return new JIfStatement(
					line, test, consequent, alternate);
		} else if (this.have(WHILE)) {
			final JExpression test = parExpression();
			final JStatement statement = statement();
			return new JWhileStatement(line, test, statement);
		} else if (this.have(RETURN)) {
			if (this.have(SEMI)) {
				return new JReturnStatement(line, null);
			}
			final JExpression expr = expression();
			this.mustBe(SEMI);
			return new JReturnStatement(line, expr);
		} else if (this.have(SEMI)) {
			return new JEmptyStatement(line);
		} else { // Must be a statementExpression
			final JStatement statement = statementExpression();
			this.mustBe(SEMI);
			return statement;
		}
	}

	/**
	 * Parse formal parameters.
	 * 
	 * <pre>
	 *   formalParameters ::= LPAREN 
	 *                          [formalParameter 
	 *                            {COMMA  formalParameter}]
	 *                        RPAREN
	 * </pre>
	 * 
	 * @return a list of formal parameters.
	 */

	private ArrayList<JFormalParameter> formalParameters() {
		final ArrayList<JFormalParameter> parameters 
		= new ArrayList<JFormalParameter>();
		this.mustBe(LPAREN);
		if (this.have(RPAREN))
			return parameters; // ()
		do {
			parameters.add(formalParameter());
		} while (this.have(COMMA));
		this.mustBe(RPAREN);
		return parameters;
	}

	/**
	 * Parse a formal parameter.
	 * 
	 * <pre>
	 *   formalParameter ::= type IDENTIFIER
	 * </pre>
	 * 
	 * @return an AST for a formalParameter.
	 */

	private JFormalParameter formalParameter() {
		final int line = this.scanner.token().line();
		final Type type = type();
		this.mustBe(IDENTIFIER);
		final String name = this.scanner.previousToken().image();
		return new JFormalParameter(line, name, type);
	}

	/**
	 * Parse a parenthesized expression.
	 * 
	 * <pre>
	 *   parExpression ::= LPAREN expression RPAREN
	 * </pre>
	 * 
	 * @return an AST for a parExpression.
	 */

	private JExpression parExpression() {
		this.mustBe(LPAREN);
		final JExpression expr = expression();
		this.mustBe(RPAREN);
		return expr;
	}

	/**
	 * Parse a local variable declaration statement.
	 * 
	 * <pre>
	 *   localVariableDeclarationStatement ::= type 
	 *                                           variableDeclarators 
	 *                                             SEMI
	 * </pre>
	 * 
	 * @return an AST for a variableDeclaration.
	 */

	private JVariableDeclaration localVariableDeclarationStatement() {
		final int line = this.scanner.token().line();
		final ArrayList<String> mods = 
				new ArrayList<String>();
		final ArrayList<JVariableDeclarator> vdecls = 
				variableDeclarators(type());
		this.mustBe(SEMI);
		return new JVariableDeclaration(line, mods, vdecls);
	}

	/**
	 * Parse variable declarators.
	 * 
	 * <pre>
	 *   variableDeclarators ::= variableDeclarator 
	 *                             {COMMA variableDeclarator}
	 * </pre>
	 * 
	 * @param type
	 *            type of the variables.
	 * @return a list of variable declarators.
	 */

	private ArrayList<JVariableDeclarator> 
	variableDeclarators(Type type) {
		ArrayList<JVariableDeclarator> 
		variableDeclarators = new ArrayList<JVariableDeclarator>();
		do {
			variableDeclarators.add(variableDeclarator(type));
		} while (this.have(COMMA));
		return variableDeclarators;
	}

	/**
	 * Parse a variable declarator.
	 * 
	 * <pre>
	 *   variableDeclarator ::= IDENTIFIER
	 *                          [ASSIGN variableInitializer]
	 * </pre>
	 * 
	 * @param type
	 *            type of the variable.
	 * @return an AST for a variableDeclarator.
	 */

	private JVariableDeclarator variableDeclarator(Type type) {
		final int line = this.scanner.token().line();
		this.mustBe(IDENTIFIER);
		final String name = this.scanner.previousToken().image();
		final JExpression initial = this.have(ASSIGN)
				? variableInitializer(type) : null;
		return new JVariableDeclarator(line, name, type, initial);
	}

	/**
	 * Parse a variable initializer.
	 * 
	 * <pre>
	 *   variableInitializer ::= arrayInitializer
	 *                         | expression
	 * </pre>
	 * 
	 * @param type
	 *            type of the variable.
	 * @return an AST for a variableInitializer.
	 */

	private JExpression variableInitializer(Type type) {
		if (this.see(LCURLY)) {
			return arrayInitializer(type);
		}
		return expression();
	}

	/**
	 * Parse an array initializer.
	 * 
	 * <pre>
	 *   arrayInitializer ::= LCURLY 
	 *                          [variableInitializer 
	 *                            {COMMA variableInitializer} [COMMA]]
	 *                        RCURLY
	 * </pre>
	 * 
	 * @param type
	 *            type of the array.
	 * @return an AST for an arrayInitializer.
	 */

	private JArrayInitializer arrayInitializer(Type type) {
		final int line = this.scanner.token().line();
		final ArrayList<JExpression> initials = 
				new ArrayList<JExpression>();
		this.mustBe(LCURLY);
		if (this.have(RCURLY)) {
			return new JArrayInitializer(line, type, initials);
		}
		initials.add(this.variableInitializer(type.componentType()));
		while (this.have(COMMA)) {
			initials.add(this.see(RCURLY)
					? null : this.variableInitializer(type
					.componentType()));
		}
		this.mustBe(RCURLY);
		return new JArrayInitializer(line, type, initials);
	}

	/**
	 * Parse arguments.
	 * 
	 * <pre>
	 *   arguments ::= LPAREN [expression {COMMA expression}] RPAREN
	 * </pre>
	 * 
	 * @return a list of expressions.
	 */

	private ArrayList<JExpression> arguments() {
		final ArrayList<JExpression> args = 
				new ArrayList<JExpression>();
		this.mustBe(LPAREN);
		if (this.have(RPAREN)) {
			return args;
		}
		do {
			args.add(expression());
		} while (this.have(COMMA));
		this.mustBe(RPAREN);
		return args;
	}

	/**
	 * Parse a type.
	 * 
	 * <pre>
	 *   type ::= referenceType 
	 *          | basicType
	 * </pre>
	 * 
	 * @return an instance of Type.
	 */

	private Type type() {
		if (this.seeReferenceType()) {
			return referenceType();
		}
		return basicType();
	}

	/**
	 * Parse a basic type.
	 * 
	 * <pre>
	 *   basicType ::= BOOLEAN | CHAR | INT
	 * </pre>
	 * 
	 * @return an instance of Type.
	 */

	private Type basicType() {
		if (this.have(BOOLEAN)) {
			return Type.BOOLEAN;
		} else if (this.have(CHAR)) {
			return Type.CHAR;
		} else if (this.have(INT)) {
			return Type.INT;
		} else {
			this.reportParserError(
				"Type sought where %s found", 
				this.scanner.token().image());
			return Type.ANY;
		}
	}

	/**
	 * Parse a reference type.
	 * 
	 * <pre>
	 *   referenceType ::= basicType LBRACK RBRACK {LBRACK RBRACK}
	 *                   | qualifiedIdentifier {LBRACK RBRACK}
	 * </pre>
	 * 
	 * @return an instance of Type.
	 */

	private Type referenceType() {
		Type type = null;
		if (!this.see(IDENTIFIER)) {
			type = this.basicType();
			this.mustBe(LBRACK);
			this.mustBe(RBRACK);
			type = new ArrayTypeName(type);
		} else {
			type = this.qualifiedIdentifier();
		}
		while (this.seeDims()) {
			this.mustBe(LBRACK);
			this.mustBe(RBRACK);
			type = new ArrayTypeName(type);
		}
		return type;
	}

	/**
	 * Parse a statement expression.
	 * 
	 * <pre>
	 *   statementExpression ::= expression // but must have 
	 *                                      // side-effect, eg i++
	 * </pre>
	 * 
	 * @return an AST for a statementExpression.
	 */

	private JStatement statementExpression() {
		final int line = this.scanner.token().line();
		final JExpression expr = expression();
		if (expr instanceof JAssignment 
				|| expr instanceof JPreIncrementOp
				|| expr instanceof JPostDecrementOp
				|| expr instanceof JMessageExpression
				|| expr instanceof JSuperConstruction
				|| expr instanceof JThisConstruction 
				|| expr instanceof JNewOp
				|| expr instanceof JNewArrayOp) {
			// So as not to save on stack
			expr.isStatementExpression = true;
		} else {
			this.reportParserError("Invalid statement expression; "
					+ "it does not have a side-effect");
		}
		return new JStatementExpression(line, expr);
	}

	/**
	 * An expression.
	 * 
	 * <pre>
	 *   expression ::= assignmentExpression
	 * </pre>
	 * 
	 * @return an AST for an expression.
	 */

	private JExpression expression() {
		return assignmentExpression();
	}

	/**
	 * Parse an assignment expression.
	 * 
	 * <pre>
	 *   assignmentExpression ::= 
	 *       conditionalAndExpression // level 13
	 *           [( ASSIGN  // conditionalExpression
	 *            | PLUS_ASSIGN // must be valid lhs
	 *            )
	 *            assignmentExpression]
	 * </pre>
	 * 
	 * @return an AST for an assignmentExpression.
	 */

	private JExpression assignmentExpression() {
		final int line = this.scanner.token().line();
		final JExpression lhs = conditionalAndExpression();
		if (this.have(ASSIGN)) {
			return new JAssignOp(line, lhs,
					this.assignmentExpression());
		} else if (this.have(PLUS_ASSIGN)) {
			return new JPlusAssignOp(line, lhs,
					this.assignmentExpression());
		} else {
			return lhs;
		}
	}

	/**
	 * Parse a conditional-and expression.
	 * 
	 * <pre>
	 *   conditionalAndExpression ::= equalityExpression // level 10
	 *                                  {LAND equalityExpression}
	 * </pre>
	 * 
	 * @return an AST for a conditionalExpression.
	 */

	private JExpression conditionalAndExpression() {
		final int line = this.scanner.token().line();
		boolean more = true;
		JExpression lhs = equalityExpression();
		while (more) {
			if (this.have(LAND)) {
				lhs = new JLogicalAndOp(
					line, lhs, equalityExpression());
			} else {
				more = false;
			}
		}
		return lhs;
	}

	/**
	 * Parse an equality expression.
	 * 
	 * <pre>
	 *   equalityExpression ::= relationalExpression  // level 6
	 *                            {EQUAL relationalExpression}
	 * </pre>
	 * 
	 * @return an AST for an equalityExpression.
	 */

	private JExpression equalityExpression() {
		final int line = this.scanner.token().line();
		boolean more = true;
		JExpression lhs = relationalExpression();
		while (more) {
			if (this.have(EQUAL)) {
				lhs = new JEqualOp(
					line, lhs, relationalExpression());
			} else {
				more = false;
			}
		}
		return lhs;
	}

	/**
	 * Parse a relational expression.
	 * 
	 * <pre>
	 *   relationalExpression ::= additiveExpression  // level 5
	 *                              [(GT | LE) additiveExpression 
	 *                              | INSTANCEOF referenceType]
	 * </pre>
	 * 
	 * @return an AST for a relationalExpression.
	 */

	private JExpression relationalExpression() {
		final int line = this.scanner.token().line();
		final JExpression lhs = additiveExpression();
		if (this.have(GT)) {
			return new JGreaterThanOp(line, lhs, 
					additiveExpression());
		} else if (this.have(LE)) {
			return new JLessEqualOp(line, lhs, 
					additiveExpression());
		} else if (this.have(INSTANCEOF)) {
			return new JInstanceOfOp(line, lhs, 
					this.referenceType());
		} else {
			return lhs;
		}
	}

	/**
	 * Parse an additive expression.
	 * 
	 * <pre>
	 *   additiveExpression ::= multiplicativeExpression // level 3
	 *                            {MINUS multiplicativeExpression}
	 * </pre>
	 * 
	 * @return an AST for an additiveExpression.
	 */

	private JExpression additiveExpression() {
		final int line = this.scanner.token().line();
		boolean more = true;
		JExpression lhs = multiplicativeExpression();
		while (more) {
			if (this.have(MINUS)) {
				lhs = new JSubtractOp(line, lhs, 
						multiplicativeExpression());
			} else if (this.have(PLUS)) {
				lhs = new JPlusOp(
					line, lhs, multiplicativeExpression());
			} else {
				more = false;
			}
		}
		return lhs;
	}

	/**
	 * Parse a multiplicative expression.
	 * 
	 * <pre>
	 *   multiplicativeExpression ::= unaryExpression  // level 2
	 *                                  {(STAR | DIV | MOD) unaryExpression}
	 * </pre>
	 * 
	 * @return an AST for a multiplicativeExpression.
	 */

	private JExpression multiplicativeExpression() {
		final int line = this.scanner.token().line();
		boolean more = true;
		JExpression lhs = unaryExpression();
		while (more) {
			if (this.have(STAR)) {
				lhs = new JMultiplyOp(
					line, lhs, this.unaryExpression());
			} else if (this.have(DIV)) {
				lhs = new JDivideOp(
					line, lhs, this.unaryExpression());
			} else if (this.have(MOD)) {
				lhs = new JModuloOp(
					line, lhs, this.unaryExpression());
			} else {
				more = false;
			}
		}
		return lhs;
	}

	/**
	 * Parse an unary expression.
	 * 
	 * <pre>
	 *   unaryExpression ::= INC unaryExpression // level 1
	 *                     | MINUS unaryExpression
	 *                     | PLUS unaryExpression
	 *                     | simpleUnaryExpression
	 * </pre>
	 * 
	 * @return an AST for an unaryExpression.
	 */

	private JExpression unaryExpression() {
		final int line = this.scanner.token().line();
		if (this.have(INC)) {
			return new JPreIncrementOp(
					line, this.unaryExpression());
		} else if (this.have(MINUS)) {
			return new JNegateOp(line, this.unaryExpression());
		} else if (this.have(PLUS)) {
			return new JUnaryPlusOp(line, this.unaryExpression());
		} else {
			return simpleUnaryExpression();
		}
	}

	/**
	 * Parse a simple unary expression.
	 * 
	 * <pre>
	 *   simpleUnaryExpression ::= LNOT unaryExpression
	 *                           | LPAREN basicType RPAREN 
	 *                               unaryExpression
	 *                           | LPAREN         
	 *                               referenceType
	 *                             RPAREN simpleUnaryExpression
	 *                           | postfixExpression
	 * </pre>
	 * 
	 * @return an AST for a simpleUnaryExpression.
	 */

	private JExpression simpleUnaryExpression() {
		final int line = this.scanner.token().line();
		if (this.have(LNOT)) {
			return new JLogicalNotOp(line, this.unaryExpression());
		} else if (this.seeCast()) {
			this.mustBe(LPAREN);
			final boolean isBasicType = this.seeBasicType();
			final Type type = type();
			this.mustBe(RPAREN);
			final JExpression expr = 
					isBasicType ? this.unaryExpression()
					: this.simpleUnaryExpression();
			return new JCastOp(line, type, expr);
		} else {
			return postfixExpression();
		}
	}

	/**
	 * Parse a postfix expression.
	 * 
	 * <pre>
	 *   postfixExpression ::= primary {selector} {DEC}
	 * </pre>
	 * 
	 * @return an AST for a postfixExpression.
	 */

	private JExpression postfixExpression() {
		final int line = this.scanner.token().line();
		JExpression primaryExpr = primary();
		while (this.see(DOT) || this.see(LBRACK)) {
			primaryExpr = selector(primaryExpr);
		}
		while (this.have(DEC)) {
			primaryExpr = new JPostDecrementOp(line, primaryExpr);
		}
		return primaryExpr;
	}

	/**
	 * Parse a selector.
	 * 
	 * <pre>
	 *   selector ::= DOT qualifiedIdentifier [arguments]
	 *              | LBRACK expression RBRACK
	 * </pre>
	 * 
	 * @param target
	 *            the target expression for this selector.
	 * @return an AST for a selector.
	 */

	private JExpression selector(JExpression target) {
		final int line = this.scanner.token().line();
		if (this.have(DOT)) {
			// Target . selector
			this.mustBe(IDENTIFIER);
			final String name = 
					this.scanner.previousToken().image();
			if (this.see(LPAREN)) {
				final ArrayList<JExpression> args = 
						this.arguments();
				return new JMessageExpression(
						line, target, name, args);
			}
			return new JFieldSelection(line, target, name);
		}
		this.mustBe(LBRACK);
		final JExpression index = this.expression();
		this.mustBe(RBRACK);
		return new JArrayExpression(line, target, index);
	}

	/**
	 * Parse a primary expression.
	 * 
	 * <pre>
	 *   primary ::= parExpression
	 *             | THIS [arguments]
	 *             | SUPER ( arguments 
	 *                     | DOT IDENTIFIER [arguments] 
	 *                     )
	 *             | literal
	 *             | NEW creator
	 *             | qualifiedIdentifier [arguments]
	 * </pre>
	 * 
	 * @return an AST for a primary.
	 */

	private JExpression primary() {
		final int line = this.scanner.token().line();
		if (this.see(LPAREN)) {
			return this.parExpression();
		} else if (this.have(THIS)) {
			if (this.see(LPAREN)) {
				return new JThisConstruction(
						line, this.arguments());
			}
			return new JThis(line);
		} else if (this.have(SUPER)) {
			if (!this.have(DOT)) {
				return new JSuperConstruction(
						line, this.arguments());
			}
			this.mustBe(IDENTIFIER);
			final String name = 
					this.scanner.previousToken().image();
			final JExpression newTarget = new JSuper(line);
			if (this.see(LPAREN)) {
				return new JMessageExpression(
						line, newTarget, null, name,
						this.arguments());
			}
			return new JFieldSelection(line, newTarget, name);
		} else if (this.have(NEW)) {
			return creator();
		} else if (this.see(IDENTIFIER)) {
			final TypeName id = this.qualifiedIdentifier();
			if (this.see(LPAREN)) {
				return new JMessageExpression(
					line, null, this.ambiguousPart(id),
					id.simpleName(), this.arguments());
			} else if (this.ambiguousPart(id) == null) {
				// A simple name
				return new JVariable(line, id.simpleName());
			} else {
				// ambiguousPart.fieldName
				return new JFieldSelection(
						line, this.ambiguousPart(id),
						null, id.simpleName());
			}
		} else {
			return literal();
		}
	}

	/**
	 * Parse a creator.
	 * 
	 * <pre>
	 *   creator ::= (basicType | qualifiedIdentifier) 
	 *                 ( arguments
	 *                 | LBRACK RBRACK {LBRACK RBRACK} 
	 *                     [arrayInitializer]
	 *                 | newArrayDeclarator
	 *                 )
	 * </pre>
	 * 
	 * @return an AST for a creator.
	 */

	private JExpression creator() {
		final int line = this.scanner.token().line();
		final Type type = this.seeBasicType() 
				? this.basicType() 
				: this.qualifiedIdentifier();
		if (this.see(LPAREN)) {
			final ArrayList<JExpression> args = 
					this.arguments();
			return new JNewOp(line, type, args);
		} else if (this.see(LBRACK)) {
			if (this.seeDims()) {
				Type expected = type;
				while (this.have(LBRACK)) {
					this.mustBe(RBRACK);
					expected = new ArrayTypeName(expected);
				}
				return this.arrayInitializer(expected);
			}
			return newArrayDeclarator(line, type);
		} else {
			this.reportParserError("( or [ sought where %s found", 
					this.scanner.token().image());
			return new JWildExpression(line);
		}
	}

	/**
	 * Parse a new array declarator.
	 * 
	 * <pre>
	 *   newArrayDeclarator ::= LBRACK expression RBRACK 
	 *                            {LBRACK expression RBRACK}
	 *                            {LBRACK RBRACK}
	 * </pre>
	 * 
	 * @param line
	 *            line in which the declarator occurred.
	 * @param type
	 *            type of the array.
	 * @return an AST for a newArrayDeclarator.
	 */

	private JNewArrayOp newArrayDeclarator(int line, Type type) {
		final ArrayList<JExpression> dimensions = 
				new ArrayList<JExpression>();
		this.mustBe(LBRACK);
		dimensions.add(this.expression());
		this.mustBe(RBRACK);
		type = new ArrayTypeName(type);
		while (this.have(LBRACK)) {
			if (this.have(RBRACK)) {
				// We're done with dimension expressions
				type = new ArrayTypeName(type);
				while (this.have(LBRACK)) {
					this.mustBe(RBRACK);
					type = new ArrayTypeName(type);
				}
				return new JNewArrayOp(line, type, dimensions);
			}
			dimensions.add(this.expression());
			type = new ArrayTypeName(type);
			this.mustBe(RBRACK);
		}
		return new JNewArrayOp(line, type, dimensions);
	}

	/**
	 * Parse a literal.
	 * 
	 * <pre>
	 *   literal ::= INT_LITERAL | CHAR_LITERAL | STRING_LITERAL
	 *             | TRUE        | FALSE        | NULL
	 * </pre>
	 * 
	 * @return an AST for a literal.
	 */

	private JExpression literal() {
		final int line = this.scanner.token().line();
		if (this.have(INT_LITERAL)) {
			return new JLiteralInt(line, 
					this.scanner.previousToken().image());
		} else if (this.have(CHAR_LITERAL)) {
			return new JLiteralChar(line, 
					this.scanner.previousToken().image());
		} else if (this.have(STRING_LITERAL)) {
			return new JLiteralString(line, 
					this.scanner.previousToken().image());
		} else if (this.have(TRUE)) {
			return new JLiteralTrue(line);
		} else if (this.have(FALSE)) {
			return new JLiteralFalse(line);
		} else if (this.have(NULL)) {
			return new JLiteralNull(line);
		} else {
			this.reportParserError("Literal sought where %s found",
					this.scanner.token().image());
			return new JWildExpression(line);
		}
	}

	// /**
	// * A tracing aid. Invoke to debug the parser at various
	// points.
	// *
	// * @param message for identifying the location of the
	// invocation.
	// */
	//
	// private void trace( String message )
	// {
	// System.err.println( "["
	// + scanner.token().line()
	// + ": "
	// + message
	// + ", looking at a: "
	// + scanner.token().tokenRep()
	// + " = " + scanner.token().image() + "]" );
	// }

}
