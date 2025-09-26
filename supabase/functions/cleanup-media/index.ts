// 1. Corrected the import to use the name from deno.json
import { createClient } from "@supabase/supabase-js";
// 2. Corrected the file path to point to core.ts
import { corsHeaders } from "../_shared/core.ts";

// Supabase client ko initialize karein environment variables se
const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
);

const BUCKET_NAME = "syncup_media";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    console.log(
      `Cron job shuru hua: "${BUCKET_NAME}" bucket se purani files clean ki ja rahi hain.`
    );

    const { data: files, error: listError } = await supabase.storage
      .from(BUCKET_NAME)
      .list();

    if (listError) throw listError;
    if (!files || files.length === 0) {
      console.log("Bucket me koi files nahi hain.");
      return new Response(
        JSON.stringify({ message: "Process karne ke liye koi files nahi hain." }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
          status: 200,
        }
      );
    }

    const twentyFourHoursAgo = new Date(
      Date.now() - 24 * 60 * 60 * 1000
    ).toISOString();
    const filesToDelete = files.filter(
      (file) => file.created_at && file.created_at < twentyFourHoursAgo
    );

    if (filesToDelete.length === 0) {
      console.log("Koi bhi file 24 ghante se purani nahi hai.");
      return new Response(
        JSON.stringify({
          message: "Delete karne ke liye koi purani file nahi hai.",
        }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
          status: 200,
        }
      );
    }

    const fileNamesToDelete = filesToDelete.map((file) => file.name);
    console.log(
      `Delete karne ke liye ${fileNamesToDelete.length} files mili hain:`,
      fileNamesToDelete
    );

    const { error: deleteError } = await supabase.storage
      .from(BUCKET_NAME)
      .remove(fileNamesToDelete);

    if (deleteError) throw deleteError;

    console.log(`Successfully ${fileNamesToDelete.length} files delete ho gayi.`);
    return new Response(
      JSON.stringify({
        message: `Successfully ${fileNamesToDelete.length} files delete ho gayi.`,
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 200,
      }
    );
  } catch (error) {
    console.error("Cron job me error:", error.message);
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
      status: 500,
    });
  }
});

