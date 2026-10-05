-- Oracle schema: run once in a dedicated, empty application schema.
-- The application never creates or changes an Oracle schema automatically.
CREATE SEQUENCE seq_board START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_reply START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE tbl_board (
    bno NUMBER(19, 0) NOT NULL,
    title VARCHAR2(200 CHAR) NOT NULL,
    content CLOB NOT NULL,
    writer VARCHAR2(50 CHAR) NOT NULL,
    regdate TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updatedate TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    replycnt NUMBER(10, 0) DEFAULT 0 NOT NULL,
    CONSTRAINT pk_board PRIMARY KEY (bno),
    CONSTRAINT ck_board_replycnt CHECK (replycnt >= 0)
);
CREATE TABLE tbl_reply (
    rno NUMBER(19, 0) NOT NULL,
    bno NUMBER(19, 0) NOT NULL,
    reply VARCHAR2(1000 CHAR) NOT NULL,
    replyer VARCHAR2(50 CHAR) NOT NULL,
    replydate TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updatedate TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_reply PRIMARY KEY (rno),
    CONSTRAINT fk_reply_board FOREIGN KEY (bno) REFERENCES tbl_board (bno)
);
CREATE INDEX idx_reply ON tbl_reply (bno, rno);
CREATE TABLE tbl_attach (
    uuid VARCHAR2(100 CHAR) NOT NULL,
    uploadpath VARCHAR2(200 CHAR) NOT NULL,
    filename VARCHAR2(255 CHAR) NOT NULL,
    filetype CHAR(1) DEFAULT '0' NOT NULL,
    bno NUMBER(19, 0) NOT NULL,
    CONSTRAINT pk_attach PRIMARY KEY (uuid),
    CONSTRAINT fk_attach_board FOREIGN KEY (bno) REFERENCES tbl_board (bno),
    CONSTRAINT ck_attach_filetype CHECK (filetype IN ('0', '1'))
);
CREATE INDEX idx_attach_board ON tbl_attach (bno);
