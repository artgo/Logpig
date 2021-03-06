/*
   Copyright 2012 Matt Weagle (mweagle@gmail.com)

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package com.logpig.mweagle.aws;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.logpig.mweagle.rolling.S3Settings;

/**
 * Runnable that puts a local file to S3
 * 
 * @author Matt Weagle (mweagle@gmail.com)
 */
public class S3FilePutRunnable implements Runnable
{

	private final Logger logger = LoggerFactory.getLogger(S3FilePutRunnable.class);

	private final String filePath;

	private final S3Settings s3Settings;

	/**
	 * Ctor
	 * 
	 * @param filePath
	 *            Path to local file to post
	 * @param s3Settings
	 *            S3Settings data that is used to construct put request
	 */
	public S3FilePutRunnable(String filePath, S3Settings s3Settings)
	{
		this.filePath = filePath;
		this.s3Settings = s3Settings;
	}

	@Override
	public void run()
	{
		boolean createBucket = false;
		boolean doExit = false;
		int attempt = 0;
		final AmazonS3Client s3Client = new AmazonS3Client(this.s3Settings.getAWSCredentials());
		while (!doExit && attempt != this.s3Settings.retryCount)
		{
			try
			{
				if (!s3Settings.mockPut)
				{
					if (createBucket)
					{
						s3Client.createBucket(this.s3Settings.bucketName, this.s3Settings.regionName);
					}
					final File logfile = new File(this.filePath);
					final String keyName = UUID.randomUUID().toString();
					final PutObjectRequest request = new PutObjectRequest(this.s3Settings.bucketName, keyName, logfile);
					s3Client.putObject(request);
				}
				else
				{
					logger.warn("Mocking file POST: {}", this.filePath);
				}
				doExit = true;
			}
			catch (AmazonServiceException ex)
			{
				createBucket = false;
				if (HttpURLConnection.HTTP_NOT_FOUND == ex.getStatusCode() && ex.getErrorCode().equals("NoSuchBucket"))
				{
					createBucket = true;
				}
				else
				{
					// If the credentials are invalid, don't keep trying...
					doExit = HttpURLConnection.HTTP_FORBIDDEN == ex.getStatusCode();
					if (doExit)
					{
						logger.error(String.format("Authentication error posting %s to AWS.  Will not retry.",
								this.filePath), ex);
					}
					else
					{
						logger.error(String.format("Failed to post %s to AWS", this.filePath), ex);
					}
				}
			}
			catch (AmazonClientException ex)
			{
				createBucket = false;
				logger.error(String.format("Failed to post %s to AWS", this.filePath), ex);
			}
			finally
			{
				// Create bucket failures don't count
				if (!createBucket)
				{
					attempt += 1;
				}
			}
		}
	}
}
