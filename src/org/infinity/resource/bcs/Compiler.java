// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource.bcs;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.infinity.NearInfinity;
import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.StatusBar;
import org.infinity.resource.Profile;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.are.AreResource;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.util.IdsMap;
import org.infinity.util.IdsMapCache;
import org.infinity.util.IdsMapEntry;
import org.infinity.util.Misc;
import org.infinity.util.io.StreamUtils;

public final class Compiler
{
  /** Indicates how to compile the script source. */
  public enum ScriptType {
    /** Treat source as full BAF resource. */
    BAF,
    /** Treat source as script trigger only. */
    TRIGGER,
    /** Treat source as script action only. */
    ACTION,
    /** Do not compile automatically. */
    CUSTOM
  }

  // Definition of triggers that don't use combined string and namespace arguments and supported engines (null = all engines)
  private static final HashMap<Long, Set<Profile.Engine>> separateNsTriggers = new HashMap<Long, Set<Profile.Engine>>();
  static {
    separateNsTriggers.put(Long.valueOf(0x403F), null); // GlobalTimerExact(S:Name*,S:Area*)
    separateNsTriggers.put(Long.valueOf(0x4040), null); // GlobalTimerExpired(S:Name*,S:Area*)
    separateNsTriggers.put(Long.valueOf(0x4041), null); // GlobalTimerNotExpired(S:Name*,S:Area*)
    separateNsTriggers.put(Long.valueOf(0x4098),
                           new HashSet<Profile.Engine>(Arrays.asList(Profile.Engine.BG2,
                                                                     Profile.Engine.EE))); // GlobalsEqual(S:Name1*,S:Name2*)
    separateNsTriggers.put(Long.valueOf(0x4099),
                           new HashSet<Profile.Engine>(Arrays.asList(Profile.Engine.BG2,
                                                                     Profile.Engine.EE))); // GlobalsGT(S:Name1*,S:Name2*)
    separateNsTriggers.put(Long.valueOf(0x409A),
                           new HashSet<Profile.Engine>(Arrays.asList(Profile.Engine.BG2,
                                                                     Profile.Engine.EE))); // GlobalsLT(S:Name1*,S:Name2*)
    separateNsTriggers.put(Long.valueOf(0x409B),
                           new HashSet<Profile.Engine>(Arrays.asList(Profile.Engine.BG2,
                                                                     Profile.Engine.EE))); // LocalsEqual(S:Name1*,S:Name2*)
    separateNsTriggers.put(Long.valueOf(0x409C),
                           new HashSet<Profile.Engine>(Arrays.asList(Profile.Engine.BG2,
                                                                     Profile.Engine.EE))); // LocalsGT(S:Name1*,S:Name2*)
    separateNsTriggers.put(Long.valueOf(0x409D),
                           new HashSet<Profile.Engine>(Arrays.asList(Profile.Engine.BG2,
                                                                     Profile.Engine.EE))); // LocalsLT(S:Name1*,S:Name2*)
    separateNsTriggers.put(Long.valueOf(0x40B5),
                           new HashSet<Profile.Engine>(Arrays.asList(Profile.Engine.BG2,
                                                                     Profile.Engine.EE))); // RealGlobalTimerExact(S:Name*,S:Area*)
    separateNsTriggers.put(Long.valueOf(0x40B6),
                           new HashSet<Profile.Engine>(Arrays.asList(Profile.Engine.BG2,
                                                                     Profile.Engine.EE))); // RealGlobalTimerExpired(S:Name*,S:Area*)
    separateNsTriggers.put(Long.valueOf(0x40B7),
                           new HashSet<Profile.Engine>(Arrays.asList(Profile.Engine.BG2,
                                                                     Profile.Engine.EE))); // RealGlobalTimerNotExpired(S:Name*,S:Area*)
    separateNsTriggers.put(Long.valueOf(0x40E5),
                           new HashSet<Profile.Engine>(Arrays.asList(Profile.Engine.EE))); // Switch(S:Global*,S:Area*)
  }

  private static final Map<String, Set<ResourceEntry>> scriptNamesCre =
      new HashMap<String, Set<ResourceEntry>>();
  private static final Set<String> scriptNamesAre = new HashSet<String>();
  private static boolean scriptNamesValid = false;

  private final SortedMap<Integer, String> errors = new TreeMap<Integer, String>();
  private final SortedMap<Integer, String> warnings = new TreeMap<Integer, String>();
  private IdsMap[] itype;
  private String emptyObject;
  private String source;  // script source
  private String code;    // compiled byte code
  private ScriptType scriptType;
  private int linenr;

  /** Globally initialize and cache creature and area references. */
  public static synchronized void restartCompiler()
  {
    if (BrowserMenuBar.getInstance().checkScriptNames()) {
      scriptNamesCre.clear();
      scriptNamesAre.clear();
      scriptNamesValid = false;
      setupScriptNames();
    }
  }

  // Returns whether the namespace argument is stored separately from the first string argument
  static boolean useSeparateNamespaceArgument(long id)
  {
    Long key = Long.valueOf(id);
    if (separateNsTriggers.containsKey(key)) {
      Set<Profile.Engine> set = separateNsTriggers.get(key);
      return (set == null || set.contains(Profile.getEngine()));
    }
    return false;
  }

  static boolean isPossibleNamespace(String string)
  {
    // TODO: simplify namespace detection
    if (string.equalsIgnoreCase("\"GLOBAL\"") ||
        string.equalsIgnoreCase("\"LOCALS\"") ||
        string.equalsIgnoreCase("\"MYAREA\"") ||
        string.equalsIgnoreCase("\"KAPUTZ\"") || // PS:T
        string.length() == 8 &&
        (string.toUpperCase(Locale.US).matches("\"\\S{2}\\d{4}\"") ||
         ResourceFactory.resourceExists(unquoteString(string) + ".ARE"))) {
      return true;
    }
    return false;
  }

  public Compiler()
  {
    this("", ScriptType.BAF);
  }

  public Compiler(ResourceEntry bafEntry) throws Exception
  {
    this(bafEntry, ScriptType.BAF);
  }

  public Compiler(ResourceEntry bafEntry, ScriptType type) throws Exception
  {
    if (bafEntry == null) {
      throw new NullPointerException();
    }
    init();
    this.scriptType = type;
    setSource(bafEntry);
  }

  public Compiler(String source)
  {
    this(source, ScriptType.BAF);
  }

  public Compiler(String source, ScriptType type)
  {
    init();
    this.scriptType = type;
    setSource(source);
  }

  /** Returns BAF script source. */
  public String getSource()
  {
    return source;
  }

  /** Set new BAF script source. */
  public void setSource(String source)
  {
    this.source = (source != null) ? source : "";
    reset();
  }

  /** Load new script source from the specified BAF resource entry. */
  public void setSource(ResourceEntry bafEntry) throws Exception
  {
    if (bafEntry == null) {
      throw new NullPointerException();
    }
    ByteBuffer buffer = bafEntry.getResourceBuffer();
    this.source = StreamUtils.readString(buffer, buffer.limit());
    reset();
  }

  /** Returns compiled script byte code. */
  public String getCode()
  {
    if (code == null) {
      compile();
    }
    return code;
  }

  /** Returns currently used script type. */
  public ScriptType getScriptType()
  {
    return scriptType;
  }

  /**
   * Specify new script type.
   * <b>Node:</b> Automatically invalidates previously compile script source.
   */
  public void setScriptType(ScriptType type)
  {
    if (type != scriptType) {
      reset();
      scriptType = type;
    }
  }

  public SortedMap<Integer, String> getErrors()
  {
    return errors;
  }

  public SortedMap<Integer, String> getWarnings()
  {
    return warnings;
  }

  public boolean hasValidScriptNames()
  {
    return scriptNamesValid;
  }

  public boolean hasScriptName(String scriptName)
  {
    if (scriptNamesValid &&
        scriptNamesCre.containsKey(scriptName.toLowerCase(Locale.ENGLISH).replaceAll(" ", ""))) {
      return true;
    }
    return false;
  }

  public Set<ResourceEntry> getResForScriptName(String scriptName)
  {
    return scriptNamesCre.get(scriptName.toLowerCase(Locale.ENGLISH).replaceAll(" ", ""));
  }

  /**
   * Compiles the currently loaded script source into BCS byte code.
   * Uses {@link #getScriptType()} to determine the correct compile action.
   * @return The compiled BCS script byte code.
   */
  public String compile()
  {
    switch (scriptType) {
      case BAF: return compileScript();
      case TRIGGER: return compileTrigger();
      case ACTION: return compileAction();
      default: throw new IllegalArgumentException("Could not determine script type");
    }
  }

  /**
   * Compiles the current script source as if defined as {@code ScriptType.BAF}.
   * @return The compiled BCS script byte code. Also available via {@link #getCode()}.
   */
  public String compileScript()
  {
    reset();
    StringBuilder sb = new StringBuilder("SC\n");
    StringTokenizer st = new StringTokenizer(source, "\n", true);

    String line = null;
    if (st.hasMoreTokens())
      line = getNextLine(st);
    while (st.hasMoreTokens()) {
      if (line == null || !line.equalsIgnoreCase("IF")) {
        String error = "Missing IF";
        errors.put(new Integer(linenr), error);
        return "Error - " + error;
      }

      sb.append("CR\n");
      compileCondition(sb, st);
      compileResponseSet(sb, st);
      sb.append("CR\n");

      line = getNextLine(st);
      while (line.length() == 0 && st.hasMoreTokens())
        line = getNextLine(st);
    }
    sb.append("SC\n");
    code = sb.toString();
    return code;
  }

  /**
   * Compiles the current script source as if defined as {@code ScriptType.Trigger}.
   * @return The compiled BCS script byte code. Also available via {@link #getCode()}.
   */
  public String compileTrigger()
  {
    return compileDialog(false);
  }

  /**
   * Compiles the current script source as if defined as {@code ScriptType.Action}.
   * @return The compiled BCS script byte code. Also available via {@link #getCode()}.
   */
  public String compileAction()
  {
    return compileDialog(true);
  }

  private void init()
  {
    if (Profile.getEngine() == Profile.Engine.PST) {
      itype = new IdsMap[]{
        IdsMapCache.get("EA.IDS"),
        IdsMapCache.get("FACTION.IDS"),
        IdsMapCache.get("TEAM.IDS"),
        IdsMapCache.get("GENERAL.IDS"),
        IdsMapCache.get("RACE.IDS"),
        IdsMapCache.get("CLASS.IDS"),
        IdsMapCache.get("SPECIFIC.IDS"),
        IdsMapCache.get("GENDER.IDS"),
        IdsMapCache.get("ALIGN.IDS")
      };
    } else if (Profile.getEngine() == Profile.Engine.IWD2) {
      itype = new IdsMap[]{
        IdsMapCache.get("EA.IDS"),
        IdsMapCache.get("GENERAL.IDS"),
        IdsMapCache.get("RACE.IDS"),
        IdsMapCache.get("CLASS.IDS"),
        IdsMapCache.get("SPECIFIC.IDS"),
        IdsMapCache.get("GENDER.IDS"),
        IdsMapCache.get("ALIGNMNT.IDS"),
        IdsMapCache.get("SUBRACE.IDS"),
        IdsMapCache.get("CLASS.IDS"),
        IdsMapCache.get("CLASSMSK.IDS")
      };
    } else {
      itype = new IdsMap[]{
        IdsMapCache.get("EA.IDS"),
        IdsMapCache.get("GENERAL.IDS"),
        IdsMapCache.get("RACE.IDS"),
        IdsMapCache.get("CLASS.IDS"),
        IdsMapCache.get("SPECIFIC.IDS"),
        IdsMapCache.get("GENDER.IDS"),
        IdsMapCache.get("ALIGN.IDS")
      };
    }
    emptyObject = compileObject(null, "");
  }

  private static synchronized void setupScriptNames()
  {
    if (scriptNamesValid) {
      return;
    }

    Runnable worker = new Runnable() {
      @Override
      public void run()
      {
        StatusBar statusBar = NearInfinity.getInstance().getStatusBar();
        String notification = "Gathering creature and area names ...";
        String oldMessage = null;
        if (statusBar != null) {
          oldMessage = statusBar.getMessage();
          statusBar.setMessage(notification);
        }

        ThreadPoolExecutor executor = Misc.createThreadPool();
        List<ResourceEntry> files = ResourceFactory.getResources("CRE");
        for (int i = 0; i < files.size(); i++) {
          Misc.isQueueReady(executor, true, -1);
          executor.execute(new CreWorker(files.get(i)));
        }

        files.clear();
        files = ResourceFactory.getResources("ARE");
        scriptNamesAre.add("none");   // default script name for many CRE resources
        for (int i = 0; i < files.size(); i++) {
          Misc.isQueueReady(executor, true, -1);
          executor.execute(new AreWorker(files.get(i)));
        }

        executor.shutdown();
        try {
          executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        if (statusBar != null) {
          if (statusBar.getMessage().startsWith(notification)) {
            statusBar.setMessage(oldMessage.trim());
          }
        }
        scriptNamesValid = true;
      }
    };
    new Thread(worker).start();
  }

  private void reset()
  {
    linenr = 0;
    errors.clear();
    warnings.clear();
    code = null;
  }

  private String compileDialog(boolean isAction)
  {
    reset();
    StringBuilder sb = new StringBuilder();
    if (Profile.getEngine() == Profile.Engine.IWD ||
        Profile.getEngine() == Profile.Engine.PST ||
        Profile.getEngine() == Profile.Engine.IWD2) {
      StringTokenizer st = new StringTokenizer(source, ")");
      while (st.hasMoreTokens()) {
        String line = st.nextToken().trim() + ')';
        linenr++;
        int index = line.indexOf("//");
        if (index != -1) {
          line = line.substring(0, index);
        }
        if (line.length() > 0 && !line.equals(")")) {
          if (isAction) {
            compileAction(sb, line);
          } else {
            compileTrigger(sb, line);
          }
        }
      }
    } else {
      StringTokenizer st = new StringTokenizer(source + "\n\n", "\n", true);
      String line = null;
      if (st.hasMoreTokens()) {
        line = getNextLine(st);
      }
      while (st.hasMoreTokens()) {
        if (isAction) {
          compileAction(sb, line);
        } else {
          compileTrigger(sb, line);
        }
        line = getNextLine(st);
        while (line.length() == 0 && st.hasMoreTokens()) {
          line = getNextLine(st);
        }
      }
    }
    code = sb.toString();
    return code;
  }

  private void checkObjectString(String definition, String value)
  {
    String name = value.substring(1, value.length() - 1).toLowerCase(Locale.ENGLISH).replaceAll(" ", "");
    if (scriptNamesValid) {
      if (name.equals("") || !(scriptNamesCre.containsKey(name) || scriptNamesAre.contains(name))) {
        warnings.put(new Integer(linenr), "Script name not found: " + definition + " - " + value);
//      } else {
//        System.out.println(definition + " - " + value + " OK");
      }
    }
  }

  private void checkString(String function, String definition, String value)
  {
    String value_str = value.substring(1, value.length() - 1);
    String value_norm = value_str.toLowerCase(Locale.ENGLISH).replaceAll(" ", "");
//    if (!definition.endsWith("*"))
//      System.out.println("Compiler.checkString: " + function + " " + definition + " " + value);
    if (value_str.isEmpty()) // ToDo: "" due to IWD2 decompiler bug?
      return;
    if (value_str.length() > 32)
      warnings.put(new Integer(linenr), "Invalid string length: " + definition + " - " + value);
    else if (definition.equalsIgnoreCase("S:Area*") ||
             definition.equalsIgnoreCase("S:Area1*") ||
             definition.equalsIgnoreCase("S:Area2*")) {
      if (!isPossibleNamespace(value)) {
        String error = "Invalid area string: " + definition + " - " + value;
        errors.put(new Integer(linenr), error);
      }
    }
    else if (definition.equalsIgnoreCase("S:Name*")) { // ToDo: need CalledByName()?
      if (scriptNamesValid) {
        if (function.equalsIgnoreCase("Dead(") ||
            function.equalsIgnoreCase("IsScriptName(") ||
            function.equalsIgnoreCase("Name(") ||
            function.equalsIgnoreCase("NumDead(") ||
            function.equalsIgnoreCase("NumDeadGT(") ||
            function.equalsIgnoreCase("NumDeadLT(")) {
          if (!(scriptNamesCre.containsKey(value_norm) || scriptNamesAre.contains(value_norm)) &&
              IdsMapCache.get("OBJECT.IDS").lookup(value) == null)
            warnings.put(new Integer(linenr), "Script name not found: " + definition + " - " + value);
        }
        else if (function.equalsIgnoreCase("SetCorpseEnabled(")) {
          if (!scriptNamesAre.contains(value_norm) &&
              IdsMapCache.get("OBJECT.IDS").lookup(value) == null)
            warnings.put(new Integer(linenr), "Script name not found: " + definition + " - " + value);
        }
      }
    }
    else if (function.equalsIgnoreCase("AttachTransitionToDoor(") && scriptNamesValid) {
        if (!scriptNamesAre.contains(value_norm) &&
            IdsMapCache.get("OBJECT.IDS").lookup(value) == null)
          warnings.put(new Integer(linenr), "Script name not found: " + definition + " - " + value);
    }
//    else if (definition.equalsIgnoreCase("S:Name*") || definition.equalsIgnoreCase("S:Column*")
//             || definition.equalsIgnoreCase("S:Entry*") || definition.equalsIgnoreCase("S:Global*")
//             || definition.equalsIgnoreCase("S:Name1*") || definition.equalsIgnoreCase("S:Name2*")
//             || definition.equalsIgnoreCase("S:Message*") || definition.equalsIgnoreCase("S:String1*")
//             || definition.equalsIgnoreCase("S:String2*") || definition.equalsIgnoreCase("S:String*")
//             || definition.equalsIgnoreCase("S:String3*") || definition.equalsIgnoreCase("S:String4*")
//             || definition.equalsIgnoreCase("S:VarTableEntry*") || definition.equalsIgnoreCase("S:Spells*")
//             || definition.equalsIgnoreCase("S:ScriptName*") || definition.equalsIgnoreCase("S:Sound1*")) {
//      // Not a resource
//    }
    else {                                                          // Resource checks
      String resourceTypes[] = new String[0];
      if (definition.equalsIgnoreCase("S:DialogFile*"))
        resourceTypes = new String[] {".DLG", ".VEF", ".VVC", ".BAM"};
      else if (definition.equalsIgnoreCase("S:CutScene*") ||
               definition.equalsIgnoreCase("S:ScriptFile*") ||
               definition.equalsIgnoreCase("S:Script*"))
        resourceTypes = new String[]{".BCS"};
      else if (definition.equalsIgnoreCase("S:Item*") ||
               definition.equalsIgnoreCase("S:Take*") ||
               definition.equalsIgnoreCase("S:Give*") ||
               definition.equalsIgnoreCase("S:Item") ||
               definition.equalsIgnoreCase("S:OldObject*"))
        resourceTypes = new String[]{".ITM"};
      else if (definition.equalsIgnoreCase("S:Sound*") ||
               definition.equalsIgnoreCase("S:Voice*"))
        resourceTypes = new String[]{".WAV"};
      else if (definition.equalsIgnoreCase("S:TextList*"))
        resourceTypes = new String[]{".2DA"};
      else if (definition.equalsIgnoreCase("S:Effect*"))
        resourceTypes = new String[]{".VEF", ".VVC", ".BAM"};
      else if (definition.equalsIgnoreCase("S:Parchment*"))
        resourceTypes = new String[]{".MOS"};
      else if (definition.equalsIgnoreCase("S:Spell*") ||
               definition.equalsIgnoreCase("S:Res*"))
        resourceTypes = new String[]{".SPL"};
      else if (definition.equalsIgnoreCase("S:Store*"))
        resourceTypes = new String[]{".STO"};
      else if (definition.equalsIgnoreCase("S:ToArea*") ||
               definition.equalsIgnoreCase("S:Areaname*") ||
               definition.equalsIgnoreCase("S:FromArea*"))
        resourceTypes = new String[]{".ARE"};
      else if (definition.equalsIgnoreCase("S:BamResRef*"))
        resourceTypes = new String[]{".BAM"};
      else if (definition.equalsIgnoreCase("S:Pool*"))
        resourceTypes = new String[]{".SRC"};
      else if (definition.equalsIgnoreCase("S:Palette*"))
        resourceTypes = new String[]{".BMP"};
      else if (definition.equalsIgnoreCase("S:ResRef*"))
        resourceTypes = Decompiler.getResRefType(function.substring(0, function.length() - 1));
      else if (definition.equalsIgnoreCase("S:Object*"))
        resourceTypes = Decompiler.getResRefType(function.substring(0, function.length() - 1));
      else if (definition.equalsIgnoreCase("S:NewObject*"))
        resourceTypes = Decompiler.getResRefType(function.substring(0, function.length() - 1));

      if (resourceTypes.length > 0) {
        for (final String resourceType : resourceTypes) {
          if (ResourceFactory.resourceExists(value_str + resourceType, true)) {
            return;
          }
        }
        warnings.put(new Integer(linenr), "Resource not found: " + definition + " - " + value);
      }
    }
//    else
//      System.out.println(definition + " - " + value);
  }

  private void compileAction(StringBuilder code, String line)
  {
    int i = line.indexOf((int)'(');
    int j = line.lastIndexOf((int)')');
    if (i == -1 || j == -1) {
      String error = "Missing parenthesis";
      errors.put(new Integer(linenr), error);
      code.append("Error - ").append(error).append('\n');
      return;
    }
    String s_action = line.substring(0, i + 1);
    String s_param = line.substring(i + 1, j);
    while (s_action.endsWith(" ("))
      s_action = s_action.substring(0, s_action.length() - 2) + '(';

    IdsMapEntry idsEntry = IdsMapCache.get("ACTION.IDS").lookup(s_action);
    if (idsEntry == null) {
      String error = s_action + " not found in ACTION.IDS";
      errors.put(new Integer(linenr), error);
      code.append("Error - ").append(error).append('\n');
      return;
    }

    String list_i[] = {"0", "0", "0"};
    String list_o[] = {emptyObject, emptyObject, emptyObject};
    String list_s[] = {null, null, null, null}; // Might be more than two because of ModifyStrings
    String list_p[] = {"0 0"};
    int index_i = 0, index_o = 0, index_s = 0, index_p = 0;

    if (s_action.equalsIgnoreCase("ActionOverride(")) {
      list_o[index_o++] = compileObject("O:Actor*", s_param.substring(0, s_param.indexOf(',')).trim());
      line = s_param.substring(s_param.indexOf(',') + 1).trim();
      i = line.indexOf((int)'(');
      j = line.lastIndexOf((int)')');
      if (i == -1 || j == -1) {
        String error = "Missing parenthesis";
        errors.put(new Integer(linenr), error);
        code.append("Error - ").append(error).append('\n');
        return;
      }
      s_action = line.substring(0, i + 1);
      s_param = line.substring(i + 1, j).trim();
      while (s_action.endsWith(" ("))
        s_action = s_action.substring(0, s_action.length() - 2) + '(';

      idsEntry = IdsMapCache.get("ACTION.IDS").lookup(s_action);
      if (idsEntry == null) {
        String error = s_action + " not found in ACTION.IDS";
        errors.put(new Integer(linenr), error);
        code.append("Error - ").append(error).append('\n');
        return;
      }
    }
    else
      index_o++;

    code.append("AC\n").append(idsEntry.getID());

    StringTokenizer actParam = new StringTokenizer(s_param, ",");
    StringTokenizer defParam = new StringTokenizer(idsEntry.getParameters(), ",");
    int defParamCount = defParam.countTokens();
    while (actParam.hasMoreTokens()) {
      String parameter = actParam.nextToken().trim();
      if (parameter.charAt(0) == '[' && parameter.charAt(parameter.length() - 1) != ']') {
        if (actParam.hasMoreTokens())
          parameter += ',' + actParam.nextToken();
        else {
          String error = "Missing end bracket - " + parameter;
          errors.put(new Integer(linenr), error);
          code.append("Error - ").append(error).append('\n');
        }
      }
      if (parameter.charAt(0) == '"' && parameter.charAt(parameter.length() - 1) != '"') {
        if (actParam.hasMoreTokens())
          parameter += ',' + actParam.nextToken();
        else {
          String error = "Missing end quote - " + parameter;
          errors.put(new Integer(linenr), error);
          code.append("Error - ").append(error).append('\n');
        }
      }
      if (!defParam.hasMoreTokens()) {
        String error = "Too many arguments - (" + idsEntry.getParameters() + ')';
        errors.put(new Integer(linenr), error);
        code.append("Error - ").append(error).append('\n');
        return;
      }
      String definition = defParam.nextToken();
      if (definition.startsWith("I:") && actParam.hasMoreTokens() && !defParam.hasMoreTokens()) // Ugly fix - commas in IDS-files
        parameter = parameter + ',' + actParam.nextToken();
      if (definition.startsWith("S:")) {
        if (index_s == 2 && parameter.charAt(0) != '"')
          list_o[index_o++] = compileObject(definition, parameter);
        else
          list_s[index_s++] = compileString(s_action, definition, parameter);
      }
      else if (definition.startsWith("I:"))
        list_i[index_i++] = compileInteger(definition, parameter);
      else if (definition.startsWith("O:"))
        list_o[index_o++] = compileObject(definition, parameter);
      else if (definition.startsWith("P:"))
        list_p[index_p++] = compilePoint(definition, parameter);
    }
    if (defParamCount > index_s + index_i + (index_o - 1) + index_p) {
      String error = "Too few arguments - (" + idsEntry.getParameters() + ')';
      errors.put(new Integer(linenr), error);
      code.append("Error - ").append(error).append('\n');
      return;
    }
    list_s = modifyStrings(list_s, (long)-1);

    code.append(list_o[0]).append('\n');
    code.append(list_o[1]).append('\n');
    code.append(list_o[2]).append('\n');
    code.append(list_i[0]).append(' ');
    code.append(list_p[0]).append(' ');
    code.append(list_i[1]).append(' ');
    code.append(list_i[2]);
    code.append(list_s[0]).append(' ');
    code.append(list_s[1]).append(' ');

    code.append("AC\n");
  }

  private void compileCondition(StringBuilder code, StringTokenizer st)
  {
    // IF last read token
    code.append("CO\n");
    String line = getNextLine(st);
    int orCount = 0;
    while (!line.equalsIgnoreCase("THEN") && line.length() > 0) {
      int newOrCount = compileTrigger(code, line);
      if (newOrCount == 0 && orCount > 0)
        orCount--;
      else if (newOrCount > 0 && orCount > 0) {
        String error = "Nested ORs not allowed";
        errors.put(new Integer(linenr), error);
        code.append("Error - ").append(error).append('\n');
      }
      else
        orCount = newOrCount;
      line = getNextLine(st);
    }
    if (orCount > 0) {
      String error = "Missing " + orCount + " trigger(s) in order to match OR()";
      errors.put(new Integer(linenr - 1), error);
      code.append("Error - ").append(error).append('\n');
    }
    if (line.length() == 0) {
      String error = "Missing THEN";
      errors.put(new Integer(linenr), error);
      code.append("Error - ").append(error).append('\n');
    }
    code.append("CO\n");
  }

  private String compileInteger(String definition, String value)
  {
    try {
      if (value.length() > 2 && value.substring(0, 2).equalsIgnoreCase("0x")) {
        int nr = Integer.parseInt(value.substring(2), 16);
        return Integer.toString(nr);
      } else {
        int nr = Integer.parseInt(value);
        return Integer.toString(nr);
      }
    } catch (NumberFormatException e) {
    }
    int i = definition.lastIndexOf((int)'*');
    if (i == -1 || definition.substring(i + 1).length() == 0) {
      String error = "Expected " + definition + " but found " + value;
      errors.put(new Integer(linenr), error);
      return "Error - " + error;
    }
    IdsMap idsmap = IdsMapCache.get(definition.substring(i + 1).toUpperCase(Locale.ENGLISH) + ".IDS");
    String code = idsmap.lookupID(value);
    if (code != null)
      return code;
    else if (value.indexOf("|") != -1) {
      long nr = (long)0;
      StringTokenizer st = new StringTokenizer(value, "|");
      while (st.hasMoreTokens()) {
        String svalue = st.nextToken().trim();
        IdsMapEntry idsentry = idsmap.lookup(svalue);
        if (idsentry == null) {
          String error = svalue + " not found in " + idsmap;
          errors.put(new Integer(linenr), error);
          return "Error - " + error;
        }
        nr += idsentry.getID();
      }
      return Integer.toString((int)nr);
    }
    else {
      String error = value + " not found in " + idsmap;
      errors.put(new Integer(linenr), error);
      return "Error - " + error;
    }
  }

  private String compileObject(String definition, String value)
  {
    long identifiers[] = {0, 0, 0, 0, 0};
    int firstIdentifier = -1;
    if (value.length() > 0 && value.charAt(0) != '"') { // Not straight string
      int i = value.indexOf((int)'(');
      while (i != -1) {
        if (value.charAt(value.length() - 1) != ')') {
          String error = "Missing end parenthesis " + value;
          errors.put(new Integer(linenr), error);
          return "Error - " + error;
        }
        IdsMapEntry idsEntry = IdsMapCache.get("OBJECT.IDS").lookup(value.substring(0, i));
        if (idsEntry == null) {
          String error = value.substring(0, i) + " not found in OBJECT.IDS";
          errors.put(new Integer(linenr), error);
          return "Error - " + error;
        }
        value = value.substring(i + 1, value.length() - 1);
        identifiers[++firstIdentifier] = idsEntry.getID();
        i = value.indexOf((int)'(');
      }
      if (value.length() == 0 && firstIdentifier >= 0)
        value = "MYSELF";
    }

    StringBuilder code = new StringBuilder("OB\n");

    if (value.length() > 0 && value.charAt(0) == '[') { // Coordinate/ObjectType
      String coord = "[-1.-1.-1.-1]";
      String iwd2 = " 0 0 ";
      while (value.charAt(0) == '[') {
        int endIndex = value.indexOf((int)']');
        if (endIndex == -1) {
          String error = "Missing end bracket";
          errors.put(new Integer(linenr), error);
          return "Error - " + error + '\n';
        }
        String rest = value.substring(endIndex);
        if (endIndex == 1) // Enable [] shortcut
          value = "ANYONE";
        else
          value = value.substring(1, endIndex);
        if (value.equalsIgnoreCase("ANYONE")) {
          if (itype.length == 7)
            code.append("0 0 0 0 0 0 0 ");
          else if (itype.length == 9)
            code.append("0 0 0 0 0 0 0 0 0 ");
          else if (itype.length == 10)
            code.append("0 0 0 0 0 0 0 0 ");
        }
        else {
          StringTokenizer st = new StringTokenizer(value, ".");
          boolean possiblecoord = true;
          StringBuilder temp = new StringBuilder();
          for (final IdsMap idsMap : itype) {
            if (st.countTokens() > 4)
              possiblecoord = false;
            if (st.hasMoreTokens()) {
              String objType = st.nextToken();
              IdsMapEntry idsEntry = idsMap.lookup(objType);
              if (idsEntry == null) {
                try {
                  temp.append(Long.parseLong(objType)).append(' ');
                } catch (NumberFormatException e) {
                  String error = objType + " not found in " + idsMap.toString().toUpperCase(Locale.ENGLISH);
                  errors.put(new Integer(linenr), error);
                  return "Error - " + error;
                }
              }
              else {
                temp.append(idsEntry.getID()).append(' ');
                possiblecoord = false;
              }
            }
            else
              temp.append("0 ");
          }
          if (possiblecoord && (Profile.getEngine() == Profile.Engine.PST ||
                                Profile.getEngine() == Profile.Engine.IWD ||
                                Profile.getEngine() == Profile.Engine.IWD2)) {
            if (code.toString().equals("OB\n")) {
              if (itype.length == 7)
                code.append("0 0 0 0 0 0 0 ");
              else if (itype.length == 9)
                code.append("0 0 0 0 0 0 0 0 0 ");
              else if (itype.length == 10)
                code.append("0 0 0 0 0 0 0 0 ");
            }
            coord = '[' + value + ']';
          }
          else if (Profile.getEngine() == Profile.Engine.IWD2) {
            int space = temp.lastIndexOf(" ");
            space = temp.substring(0, space).lastIndexOf(" ");
            space = temp.substring(0, space).lastIndexOf(" ");
            code.append(temp.substring(0, space + 1));
            iwd2 = temp.substring(space);
          }
          else
            code.append(temp);
        }
        int index = rest.indexOf((int)'[');
        if (index != -1)
          value = rest.substring(index);
      }
      for (int i = firstIdentifier; i >= 0; i--) {
        code.append(identifiers[i]).append(' ');
      }
      for (int i = firstIdentifier + 1; i < identifiers.length; i++) {
        code.append(identifiers[i]).append(' ');
      }
      if (Profile.getEngine() == Profile.Engine.PST || Profile.getEngine() == Profile.Engine.IWD) {
        code.append(coord).append(" \"\"OB");
      } else if (Profile.getEngine() == Profile.Engine.IWD2) {
        code.append(coord).append(" \"\"").append(iwd2).append("OB");
      } else {
        code.append("\"\"OB");
      }
    }

    else if (value.length() > 0 && value.charAt(0) == '"') { // String
      if (value.charAt(value.length() - 1) != '"') {
        String error = "Missing end quote - " + value;
        errors.put(new Integer(linenr), error);
        return "Error - " + error;
      }
      if (itype.length == 7) {
        code.append("0 0 0 0 0 0 0 ");
      } else if (itype.length == 9) {
        code.append("0 0 0 0 0 0 0 0 0 ");
      } else if (itype.length == 10) {
        code.append("0 0 0 0 0 0 0 0 ");
      }
      for (int i = firstIdentifier; i >= 0; i--) {
        code.append(identifiers[i]).append(' ');
      }
      for (int i = firstIdentifier + 1; i < identifiers.length; i++) {
        code.append(identifiers[i]).append(' ');
      }
      if (Profile.getEngine() == Profile.Engine.PST || Profile.getEngine() == Profile.Engine.IWD) {
        code.append("[-1.-1.-1.-1] ").append(value).append("OB");
      } else if (Profile.getEngine() == Profile.Engine.IWD2) {
        code.append("[-1.-1.-1.-1] ").append(value).append(" 0 0 OB");
      } else {
        code.append(value).append("OB");
      }
      checkObjectString(definition, value);
    }

    else {
      if (itype.length == 7)
        code.append("0 0 0 0 0 0 0 ");
      else if (itype.length == 9)
        code.append("0 0 0 0 0 0 0 0 0 ");
      else if (itype.length == 10)
        code.append("0 0 0 0 0 0 0 0 ");
      String coord;
      if (value.endsWith("]")) {
        coord = value.substring(value.indexOf((int)'['));
        value = value.substring(0, value.indexOf((int)'['));
      } else {
        coord = "[-1.-1.-1.-1]";
      }
      IdsMapEntry idsEntry = IdsMapCache.get("OBJECT.IDS").lookup(value);
      if (idsEntry == null) {
        identifiers[++firstIdentifier] = (long)0;
      } else {
        identifiers[++firstIdentifier] = idsEntry.getID();
      }
      if (coord.equals("[-1.-1.-1.-1]") && idsEntry == null && !value.equals("")) {
        String error = "Unknown symbol - " + value;
        errors.put(new Integer(linenr), error);
        return "Error - " + error;
      }
      for (int i = firstIdentifier; i >= 0; i--) {
        code.append(identifiers[i]).append(' ');
      }
      for (int i = firstIdentifier + 1; i < identifiers.length; i++) {
        code.append(identifiers[i]).append(' ');
      }
      if (Profile.getEngine() == Profile.Engine.PST || Profile.getEngine() == Profile.Engine.IWD) {
        code.append("[-1.-1.-1.-1] \"\"OB");
      } else if (Profile.getEngine() == Profile.Engine.IWD2) {
        code.append(coord).append(" \"\" 0 0 OB");
      } else {
        code.append("\"\"OB");
        if (!coord.equals("[-1.-1.-1.-1]")) {
          String error = "Missing parenthesis?";
          errors.put(new Integer(linenr), error);
          return "Error - " + error;
        }
      }
    }
    return code.toString();
  }

  private String compilePoint(String definition, String value)
  {
    if (value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']') {
      value = value.substring(1, value.length() - 1); // Remove '[' and ']'
      StringTokenizer st = new StringTokenizer(value, ".");
      StringBuilder code = new StringBuilder();
      int countPeriod = 0;
      for (int i = 0; i < value.length(); i++)
        if (value.charAt(i) == '.')
          countPeriod++;
      if (countPeriod != st.countTokens() - 1) {
        String error = '[' + value + "] - arguments missing";
        errors.put(new Integer(linenr), error);
        return "Error - " + error;
      }
      try {
        while (st.hasMoreTokens()) {
          String s = st.nextToken();
          if (code.length() > 0)
            code.append(' ');
          code.append(Integer.parseInt(s));
        }
        return code.toString();
      } catch (NumberFormatException e) {
        String error = '[' + value + "] must contain numbers only";
        errors.put(new Integer(linenr), error);
        return "Error - " + error;
      }
    }
    String error = "Expected " + definition + " but found " + value;
    errors.put(new Integer(linenr), error);
    return "Error - " + error;
  }

  private void compileResponseSet(StringBuilder code, StringTokenizer st)
  {
    // THEN last read token
    code.append("RS\n");
    String line = getNextLine(st);
    boolean firstresponse = true;
    while (!line.equalsIgnoreCase("END") && line.length() > 0) {
      if (line.length() > 7 && line.substring(0, 8).equalsIgnoreCase("RESPONSE")) {
        if (!firstresponse)
          code.append("RE\n");
        code.append("RE\n");
        int i = line.indexOf((int)'#');
        if (i == -1) {
          String error = "Missing # in RESPONSE";
          errors.put(new Integer(linenr), error);
          code.append("Error - ").append(error).append('\n');
          return;
        }
        code.append(line.substring(i + 1));
        firstresponse = false;
      }
      else
        compileAction(code, line);
      line = getNextLine(st);
    }
    if (line.length() == 0) {
      String error = "Missing END";
      errors.put(new Integer(linenr), error);
      code.append("Error - ").append(error).append('\n');
    }
    code.append("RE\nRS\n");
  }

  private String compileString(String function, String definition, String value)
  {
    checkString(function, definition, value);
    return value;
  }

  private int compileTrigger(StringBuilder code, String line) // returns n if trigger = OR(n)
  {
    String flag;
    if (line.charAt(0) == '!') {
      flag = "1";
      line = line.substring(1, line.length());
    }
    else
      flag = "0";

    int i = line.indexOf((int)'(');
    int j = line.lastIndexOf((int)')');
    if (i == -1 || j == -1) {
      String error = "Missing parenthesis";
      errors.put(new Integer(linenr), error);
      code.append("Error - ").append(error).append('\n');
      return 0;
    }
    String s_trigger = line.substring(0, i + 1);
    String s_param = line.substring(i + 1, j);
    while (s_trigger.endsWith(" ("))
      s_trigger = s_trigger.substring(0, s_trigger.length() - 2) + '(';

    IdsMapEntry idsEntry = IdsMapCache.get("TRIGGER.IDS").lookup(s_trigger);
    if (idsEntry == null) {
      String error = s_trigger + " not found in TRIGGER.IDS";
      errors.put(new Integer(linenr), error);
      code.append("Error - ").append(error).append('\n');
      return 0;
    }

    code.append("TR\n").append(idsEntry.getID()).append(' ');
    String integers[] = {"0", "0", "0"};
    String object = null;
    String strings[] = {null, null, null, null};
    String point = "[0,0]";
    int indexI = 0, indexS = 0;

    StringTokenizer actParam = new StringTokenizer(s_param, ",");
    StringTokenizer defParam = new StringTokenizer(idsEntry.getParameters(), ",");
    int defParamCount = defParam.countTokens(), actParamCount = 0;
    while (actParam.hasMoreTokens()) {
      String parameter = actParam.nextToken().trim();
      if (parameter.charAt(0) == '"' && parameter.charAt(parameter.length() - 1) != '"') {
        if (actParam.hasMoreTokens())
          parameter += ',' + actParam.nextToken();
        else {
          String error = "Missing end quote - " + parameter;
          errors.put(new Integer(linenr), error);
          code.append("Error - ").append(error).append('\n');
          return 0;
        }
      }
      if (parameter.charAt(0) == '[' && parameter.charAt(parameter.length() - 1) != ']') {
        if (actParam.hasMoreTokens())
          parameter += ',' + actParam.nextToken();
        else {
          String error = "Missing end bracket - " + parameter;
          errors.put(new Integer(linenr), error);
          code.append("Error - ").append(error).append('\n');
          return 0;
        }
      }
      if (!defParam.hasMoreTokens()) {
        String error = "Too many arguments - (" + idsEntry.getParameters() + ')';
        errors.put(new Integer(linenr), error);
        code.append("Error - ").append(error).append('\n');
        return 0;
      }
      String definition = defParam.nextToken();
      if (definition.startsWith("I:") && actParam.hasMoreTokens() && !defParam.hasMoreTokens()) // Ugly fix - commas in IDS-files
        parameter = parameter + ',' + actParam.nextToken();
      if (definition.startsWith("S:"))
        strings[indexS++] = compileString(s_trigger, definition, parameter);
      else if (definition.startsWith("I:"))
        integers[indexI++] = compileInteger(definition, parameter);
      else if (definition.startsWith("O:"))
        object = compileObject(definition, parameter);
      else if (definition.startsWith("P:"))
        point = parameter.replaceFirst("\\.", ",");     // be consistent with WeiDU
      actParamCount++;
    }
    if (defParamCount > actParamCount) {
      String error = "Too few arguments - (" + idsEntry.getParameters() + ')';
      errors.put(new Integer(linenr), error);
      code.append("Error - ").append(error).append('\n');
      return 0;
    }
    if (object == null)
      object = emptyObject;

    strings = modifyStrings(strings, idsEntry.getID());

    code.append(integers[0]).append(' ');
    code.append(flag).append(' ');
    code.append(integers[1]).append(' ');
    code.append(integers[2]).append(' ');
    if (Profile.getEngine() == Profile.Engine.PST) {
      code.append(point).append(' ');
    }
    code.append(strings[0]).append(' ');
    code.append(strings[1]).append(' ');
    code.append(object).append('\n');

    code.append("TR\n");
    if (s_trigger.equalsIgnoreCase("OR("))
      return Integer.parseInt(integers[0]);
    return 0;
  }

  private String getNextLine(StringTokenizer st)
  {
    if (!st.hasMoreTokens())
      return "";
    String line = st.nextToken();
    if (!line.equals("\n") && st.hasMoreTokens())
      st.nextToken();
    linenr++;
    int i = line.indexOf("//");
    if (i != -1)
      line = line.substring(0, i);
    line = line.trim();
    if (line.length() == 0)
      line = getNextLine(st);
    return line;
  }

  private String[] modifyStrings(String strings[], long id)
  {
    String newStrings[] = new String[strings.length];
    int newIndex = 0;
    for (int i = 0; i < strings.length; i++) {
      String s = strings[i];
      if (s != null) {
        boolean q_start = (s.length() > 0 && s.charAt(0) == '"');
        boolean q_end = (s.length() > 1 && s.charAt(s.length() - 1) == '"');
        if (!q_start && q_end) {
          errors.put(new Integer(linenr), "Missing begin quote - " + s);
        } else if (q_start && !q_end) {
          errors.put(new Integer(linenr), "Missing end quote - " + s);
        } else if (!q_start && !q_end) {
          errors.put(new Integer(linenr), "Missing quotes - " + s);
        }
        s = quoteString(s);

        if (useSeparateNamespaceArgument(id)) {
          newStrings[newIndex++] = s;
        } else if (newIndex > 0 && isPossibleNamespace(s) && !isPossibleNamespace(newStrings[newIndex - 1])) {
          newStrings[newIndex - 1] = quoteString(unquoteString(s) + unquoteString(newStrings[newIndex - 1]));
        } else if (newIndex > 1) {
          newStrings[newIndex - 1] = quoteString(unquoteString(newStrings[newIndex - 1]) + ":" + unquoteString(s));
        } else {
          newStrings[newIndex++] = s;
        }
      } else {
        newStrings[i] = "\"\"";
      }
    }

    while (newIndex < newStrings.length) {
      newStrings[newIndex++] = "\"\"";
    }

    return newStrings;
  }

  private static String unquoteString(String s)
  {
    if (s != null) {
      int startIdx = (s.length() > 0 && s.charAt(0) == '"') ? 1 : 0;
      int endIdx = (s.length() > 1 && s.charAt(s.length() - 1) == '"') ? s.length() - 1 : s.length();
      return s.substring(startIdx, endIdx);
    } else {
      return null;
    }
  }

  private static String quoteString(String s)
  {
    if (s != null) {
      StringBuilder sb = new StringBuilder(s.length() + 2);
      if (s.length() == 0 || s.charAt(0) != '"') {
        sb.append('"');
      }
      sb.append(s);
      if (s.length() < 2 || s.charAt(s.length() - 1) != '"') {
        sb.append('"');
      }
      return sb.toString();
    } else {
      return null;
    }
  }

//-------------------------- INNER CLASSES --------------------------

  private static class CreWorker implements Runnable
  {
    final ResourceEntry entry;

    public CreWorker(ResourceEntry entry)
    {
      this.entry = entry;
    }

    @Override
    public void run()
    {
      if (entry != null) {
        try {
          CreResource.addScriptName(scriptNamesCre, entry);
        }
        catch (Exception e) {
        }
      }
    }
  }

  private static class AreWorker implements Runnable
  {
    final ResourceEntry entry;

    public AreWorker(ResourceEntry entry)
    {
      this.entry = entry;
    }

    @Override
    public void run()
    {
      if (entry != null) {
        try {
          AreResource.addScriptNames(scriptNamesAre, entry.getResourceBuffer());
        }
        catch (Exception e) {
        }
      }
    }
  }
}

