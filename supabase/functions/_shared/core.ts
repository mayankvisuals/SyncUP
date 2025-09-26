// Yeh headers zaroori hain taaki aapka function browser se aane wali requests
// ko aachi tarah se handle kar sake, khaaskar cron jobs ke liye.
export const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};
