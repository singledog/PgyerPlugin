package com.cyx.UploadToPgyPlugin;

public class PgyResponse{

	private int code;

	private Data data;

	public void setCode(int code) {
		this.code = code;
	}

	public void setData(Data data) {
		this.data = data;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	private String message;

	public int getCode(){
		return code;
	}

	public Data getData(){
		return data;
	}

	public String getMessage(){
		return message;
	}
}
